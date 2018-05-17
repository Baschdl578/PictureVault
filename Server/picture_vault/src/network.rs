use ascii::AsciiString;
use base64;
use chrono::prelude::{DateTime, Local};
use chrono::{Datelike, TimeZone};
use fs_extra::file::{move_file, CopyOptions};
use futures::Future;
use futures::sync::oneshot;
use multipart::server::save::{PartialReason, SaveDir, SavedData, TempDir};
use multipart::server::{Entries, Multipart, SaveResult};
use tiny_http as http;

use std::error::Error;
use std::fs::{self, File};
use std::io::Seek;
use std::io::SeekFrom;
use std::io::{BufRead, BufReader, Read, Write};
use std::path::Path;
use std::str::FromStr;
use std::sync::Arc;
use std::thread;

use common;
use database;
use maintenance;

pub fn answer(request: http::Request) {
    let mut user = String::new();
    let mut pass = String::new();
    let mut range: u64 = 0;
    {
        let headers = request.headers();
        let mut found = false;
        for h in headers {
            let mut field = String::new();
            field.push_str(h.field.as_str().as_str());
            if field == "Authorization".to_string() {
                let mut coded_value = String::new();
                let mut coded_value1 = String::new();
                coded_value1.push_str(h.value.as_str());
                if coded_value1.starts_with("Basic ") {
                    coded_value = coded_value1.split_off(6);
                }
                let bytes = match base64::decode(&coded_value) {
                    Ok(s) => s,
                    Err(_) => {
                        continue;
                    }
                };
                let value = match String::from_utf8(bytes) {
                    Ok(s) => s,
                    Err(_) => {
                        continue;
                    }
                };
                let split: Vec<String> = value.split(":").map(|s| s.to_string()).collect();
                user.push_str(&split[0]);
                pass.push_str(&split[1]);
            }
            if field == "Range".to_string() {
                found = true;
                let mut coded_value = String::new();
                let mut coded_value1 = String::new();
                coded_value1.push_str(h.value.as_str());
                if coded_value1.starts_with("bytes=") {
                    coded_value = coded_value1.split_off(6);
                }
                if coded_value.ends_with("-") {
                    let length = coded_value.len();
                    let _ = coded_value.split_off(length - 1);
                } else {
                    let split: Vec<String> =
                        coded_value.split("-").map(|s| s.to_string()).collect();
                    coded_value = String::new();
                    coded_value.push_str(&split[0]);
                }
                range = match coded_value.parse::<u64>() {
                    Ok(n) => n,
                    Err(_) => 0,
                };
                println!("Range request: {}", range);
            }
            if found && user.len() > 0 && pass.len() > 0 {
                break;
            }
        }
    }
    let uid = match database::get_user_id_and_verify(&user, &pass) {
        Ok(i) => i,
        Err(-5) => {
            let mut message = String::from("Bad Login: ");
            message.push_str(&user);
            message.push_str(" with password: ");
            message.push_str(&pass);
            message.push_str(&format!(" and URL: {}", request.url()));
            println!("{}", &message);
            let response =
                http::Response::from_string(message).with_status_code(http::StatusCode::from(401));
            let _ = request.respond(response);
            return;
        }
        Err(_) => {
            internal_error(request, "Error while verifying login");
            return;
        }
    };

    if request.url().starts_with("/media/load/") {
        get_media_url(String::from(request.url()), uid, request, range);
        return;
    }
    if request.url().starts_with("/media/stream/") {
        stream_media(String::from(request.url()), uid, request);
        return;
    }

    match request.url() {
        "/test/echo" => {
            echo(request);
            return;
        }
        "/library/all" => {
            get_libs(request, uid);
            return;
        }
        "/library/media" => {
            get_lib_mediaids(request, uid);
            return;
        }
        "/media/thumb" => {
            get_mediathumb(request, uid);
            return;
        }
        "/media/info" => {
            get_mediainfo(request, uid);
            return;
        }
        "/media/load" => {
            get_media_stream(request, uid, range);
            return;
        }
        "/media/upload" => {
            mediaupload(request, uid);
            return;
        }
        "/media/upload/ios" => {
            mediaupload_multipart(request, uid);
            return;
        }
        "/media/search" => {
            mediasearch(request, uid);
            return;
        }
        "/lastsync/get" => {
            get_lastsync(request, uid);
            return;
        }
        "/lastsync/set" => {
            set_lastsync(request, uid);
            return;
        }
        "/pulse" => {
            echo(request);
            return;
        }
        _ => {
            url_not_found(request);
            return;
        }
    }
}

pub fn echo(mut request: http::Request) {
    let mut string = String::new();
    {
        let reader = request.as_reader();
        let _ = reader.read_to_string(&mut string);
    }
    let response = http::Response::from_string(string);
    let _ = request.respond(response);
}

fn get_media_intern(uid: u64, media_id: u64, request: http::Request, range: u64) {
    let mut picpath = String::new();
    let path;
    let pic = match database::get_mediainfo(uid, media_id) {
        Ok(p) => p,
        Err(_) => {
            gone(request);
            return;
        }
    };
    picpath.push_str(&pic.get_full_path());
    path = Path::new(&picpath);

    let mut file = match File::open(path) {
        Ok(f) => f,
        Err(e) => {
            internal_error(request, &format!("Could not open file: {}", e));
            return;
        }
    };

    let length = match file.metadata() {
        Ok(m) => m.len(),
        Err(e) => {
            internal_error(request, &format!("Could not read file: {}", e));
            return;
        }
    };

    let mut status = http::StatusCode(200);
    let mut response;
    if range > 0 {
        status = http::StatusCode(206);
    }
    if range >= length {
        status = http::StatusCode(416);
    }

    if range > 0 {
        match file.seek(SeekFrom::Start(range)) {
            Ok(_) => {
                //nothing
            }
            Err(e) => {
                internal_error(request, &format!("Could not seek in file: {}", e));
                return;
            }
        };
    }

    response = http::Response::new(
        status,
        Vec::with_capacity(0),
        file,
        Some(length as usize - range as usize),
        None,
    );

    let header = http::Header {
        field: match http::HeaderField::from_str("Content-Range") {
            Ok(f) => f,
            Err(_) => {
                internal_error(request, "Could not create header field");
                return;
            }
        },
        value: match AsciiString::from_str(&format!("bytes {}-{}", range, length)) {
            Ok(s) => s,
            Err(_) => {
                internal_error(request, "Could not create header value string");
                return;
            }
        },
    };
    response.add_header(header);

    let header = http::Header {
        field: match http::HeaderField::from_str("Accept-Ranges") {
            Ok(f) => f,
            Err(_) => {
                internal_error(request, "Could not create header field");
                return;
            }
        },
        value: match AsciiString::from_str("none") {
            Ok(s) => s,
            Err(_) => {
                internal_error(request, "Could not create header value string");
                return;
            }
        },
    };
    response.add_header(header);

    let _ = request.respond(response);
}

pub fn get_media_url(url: String, uid: u64, request: http::Request, range: u64) {
    let string = String::from(url.trim_matches('/'));
    let values: Vec<&str> = string.split("/").collect::<Vec<&str>>();
    let mut id_str = String::new();
    if values.len() > 2 {
        id_str.push_str(values[2]);
    } else {
        internal_error(request, "Could not read media id");
        return;
    }
    let id = match id_str.parse::<u64>() {
        Ok(n) => n,
        Err(_) => {
            internal_error(request, "Could not read media id");
            return;
        }
    };
    get_media_intern(uid, id, request, range);
}

pub fn stream_media(url: String, uid: u64, request: http::Request) {
    let string = String::from(url.trim_matches('/'));
    let values: Vec<&str> = string.split("/").collect::<Vec<&str>>();
    let mut id_str = String::new();
    if values.len() > 3 {
        id_str.push_str(values[2]);
    } else {
        internal_error(request, "Could not read media id");
        return;
    }
    let id = match id_str.parse::<u64>() {
        Ok(n) => n,
        Err(_) => {
            internal_error(request, "Could not read media id");
            return;
        }
    };
    let pic = match database::get_mediainfo(uid, id) {
        Ok(p) => p,
        Err(_) => {
            gone(request);
            return;
        }
    };

    if values[3] == "mpd" {
        let mpd_path = match pic.get_mpd_path() {
            Ok(p) => p,
            Err(_) => {
                internal_error(request, "Could not load mpd path");
                return;
            }
        };
        let path = Path::new(&mpd_path);
        let out = match File::open(path) {
            Ok(v) => v,
            Err(_) => {
                //Retry once
                let tmp = match File::open(path) {
                    Ok(v) => v,
                    Err(_) => {
                        internal_error(request, &format!("Could not open file: {}", &mpd_path));
                        return;
                    }
                };
                tmp
            }
        };
        let response = http::Response::from_file(out);
        let _ = request.respond(response);
    } else {
        let _ = pic.prepare_for_streaming();
        let mut filepath = pic.get_streaming_path();
        if !filepath.ends_with("/") {
            filepath.push('/');
        }
        filepath.push_str(values[3]);
        let path = Path::new(&filepath);
        let out = match File::open(path) {
            Ok(v) => v,
            Err(_) => {
                //Retry once
                let tmp = match File::open(path) {
                    Ok(v) => v,
                    Err(_) => {
                        internal_error(request, &format!("Could not open file: {}", &filepath));
                        return;
                    }
                };
                tmp
            }
        };
        let response = http::Response::from_file(out);
        let _ = request.respond(response);
    }
}

pub fn get_media_stream(mut request: http::Request, uid: u64, range: u64) {
    let mut id_str: String = String::new();
    {
        let mut reader = BufReader::new(request.as_reader());
        let _ = reader.read_line(&mut id_str);
    }
    let id = match id_str.parse::<u64>() {
        Ok(n) => n,
        Err(_) => {
            internal_error(request, "Could not read picture id");
            return;
        }
    };
    get_media_intern(uid, id, request, range);
}

pub fn get_libs(request: http::Request, uid: u64) {
    let libs = match database::get_libs(uid) {
        Ok(l) => l,
        Err(_) => {
            internal_error(request, "Could not load libraries from database");
            return;
        }
    };
    let iter = libs.into_iter();
    let mut out = String::new();
    let mut first = true;
    for (id, name, count, (thumb1, thumb2, thumb3, thumb4)) in iter {
        if first {
            first = false;
        } else {
            out.push('\n');
        }
        out.push_str(&id.to_string());
        out.push(';');
        out.push_str(&name);
        out.push(';');
        out.push_str(&count.to_string());
        if thumb1 > 0 {
            out.push(';');
            out.push_str(&thumb1.to_string());
        }
        if thumb2 > 0 {
            out.push(';');
            out.push_str(&thumb2.to_string());
        }
        if thumb3 > 0 {
            out.push(';');
            out.push_str(&thumb3.to_string());
        }
        if thumb4 > 0 {
            out.push(';');
            out.push_str(&thumb4.to_string());
        }
    }

    let response = http::Response::from_string(out);
    let _ = request.respond(response);
}

pub fn get_lib_mediaids(mut request: http::Request, uid: u64) {
    let mut id_str = String::new();
    {
        let mut reader = BufReader::new(request.as_reader());
        let _ = reader.read_line(&mut id_str);
    }
    let id = match id_str.parse::<u64>() {
        Ok(n) => n,
        Err(_) => {
            internal_error(request, "Could not read library id");
            return;
        }
    };
    let ids = match database::get_pics_by_lib(id, uid) {
        Ok(i) => i,
        Err(_) => {
            internal_error(request, "Could not load library pics from database");
            return;
        }
    };
    let mut out = String::new();
    let mut first = true;
    for (id, name, duration, size) in ids {
        if first {
            first = false;
        } else {
            out.push('\n');
        }
        out.push_str(&format!("{};{};{};{}", id, name, duration, size));
    }

    let response = http::Response::from_string(out);
    let _ = request.respond(response);
}

pub fn get_mediainfo(mut request: http::Request, uid: u64) {
    let mut id: String = String::new();
    let media;
    {
        let mut reader = BufReader::new(request.as_reader());
        let _ = reader.read_line(&mut id);
    }
    let pic_id = match id.parse::<u64>() {
        Ok(n) => n,
        Err(_) => {
            internal_error(request, "Could not read picture id");
            return;
        }
    };

    media = match database::get_mediainfo(uid, pic_id) {
        Ok(p) => p,
        Err(_) => {
            gone(request);
            return;
        }
    };

    let response = http::Response::from_string(media.to_string());
    let _ = request.respond(response);
}

pub fn get_mediathumb(mut request: http::Request, uid: u64) {
    let mut picpath = String::new();
    let path;
    let mut id: String = String::new();
    {
        let mut reader = BufReader::new(request.as_reader());
        let _ = reader.read_line(&mut id);
    }
    let pic_id = match id.parse::<u64>() {
        Ok(n) => n,
        Err(_) => {
            internal_error(request, "Could not read picture id");
            return;
        }
    };

    let pic = match database::get_mediainfo(uid, pic_id) {
        Ok(p) => p,
        Err(_) => {
            gone(request);
            return;
        }
    };
    let thumbpath = match pic.get_thumbnail(true) {
        Ok(s) => s,
        Err(_) => {
            internal_error(request, "Could not get thumbnail path");
            return;
        }
    };
    picpath.push_str(&thumbpath);
    path = Path::new(&picpath);
    let out = match File::open(path) {
        Ok(v) => v,
        Err(_) => {
            //Retry once
            let tmp = match File::open(path) {
                Ok(v) => v,
                Err(_) => {
                    internal_error(request, "Could not open file");
                    return;
                }
            };
            tmp
        }
    };
    let response = http::Response::from_file(out);
    let _ = request.respond(response);
}

fn mediaupload_multipart(mut request: http::Request, uid: u64) {
    let tmp_path = match database::get_userpath(uid) {
        Ok(p) => p,
        Err(_) => {
            internal_error(request, "Could not load userpath from database");
            return;
        }
    };
    match create_dirs(&tmp_path, uid) {
        Ok(_) => {
            //nothing
        }
        Err(_) => {
            internal_error(request, "Could not create temporary directory");
            return;
        }
    }
    let tmp_dir = match TempDir::new_in(&tmp_path, ".tmp") {
        Ok(d) => d,
        Err(e) => {
            common::log_error(
                &"network.rs",
                &"mediaupload_multipart",
                line!(),
                &format!("Could not open temp dir: {}", e),
            );
            internal_error(request, &format!("Could not open temp dir: {}", e));
            return;
        }
    };

    let dir = match TempDir::new(&tmp_path) {
        Ok(d) => d,
        Err(e) => {
            common::log_error(
                &"network.rs",
                &"mediaupload_multipart",
                line!(),
                &format!("Could not open temp dir: {}", e),
            );
            internal_error(request, &format!("Could not open temp dir: {}", e));
            return;
        }
    };
    let mut entries = Entries::new(SaveDir::Temp(dir));

    let mut error = false;
    let mut message = format!("Multipart data corrupt");
    let mut not_multipart = false;

    match Multipart::from_request(&mut request) {
        Ok(mut multipart) => {
            // Fetching all data and processing it.
            // save().temp() reads the request fully, parsing all fields and saving all files
            // in a new temporary directory under the OS temporary directory.
            match multipart.save().with_temp_dir(tmp_dir) {
                SaveResult::Full(entr) => {
                    entries = entr;
                }
                SaveResult::Error(e) => {
                    message = format!("Error parsing multipart: {}", e.description());
                    error = true;
                }
                SaveResult::Partial(_, reason) => {
                    match reason {
                        PartialReason::CountLimit => {
                            message = format!("Partial because count limit")
                        }
                        PartialReason::SizeLimit => message = format!("Partial because size limit"),
                        PartialReason::IoError(e) => {
                            message = format!("Partial because io error: {}", e.description())
                        }
                        PartialReason::Utf8Error(e) => {
                            message = format!("Partial because utf8 error: {}", e)
                        }
                    }
                    error = true;
                }
            }
        }
        Err(_) => {
            not_multipart = true;
        }
    }

    if not_multipart {
        internal_error(request, "Request is not multipart");
        return;
    }
    if error {
        internal_error(request, &message);
        return;
    }

    let bucket = match extract_from_form(&entries, "bucket") {
        Ok(s) => s,
        Err(_) => {
            internal_error(request, "Could not read bucket");
            return;
        }
    };
    let filename = match extract_from_form(&entries, "filename") {
        Ok(s) => s,
        Err(_) => {
            internal_error(request, "Could not read filename");
            return;
        }
    };
    let created = match extract_from_form(&entries, "created") {
        Ok(v) => match v.parse::<u64>() {
            Ok(v) => v,
            Err(_) => 0,
        },
        Err(_) => 0,
    };
    let modified = match extract_from_form(&entries, "modified") {
        Ok(v) => match v.parse::<u64>() {
            Ok(v) => v,
            Err(_) => 0,
        },
        Err(_) => 0,
    };
    let lat = match extract_from_form(&entries, "latitude") {
        Ok(v) => match v.parse::<f64>() {
            Ok(v) => v,
            Err(_) => 0.0,
        },
        Err(_) => 0.0,
    };
    let lon = match extract_from_form(&entries, "longitude") {
        Ok(v) => match v.parse::<f64>() {
            Ok(v) => v,
            Err(_) => 0.0,
        },
        Err(_) => 0.0,
    };
    let duration = match extract_from_form(&entries, "duration") {
        Ok(v) => match v.parse::<i64>() {
            Ok(v) => v,
            Err(_) => -1,
        },
        Err(_) => -1,
    };
    let h_res = match extract_from_form(&entries, "horizontal resolution") {
        Ok(v) => match v.parse::<u64>() {
            Ok(v) => v,
            Err(_) => 0,
        },
        Err(_) => 0,
    };
    let v_res = match extract_from_form(&entries, "vertical resolution") {
        Ok(v) => match v.parse::<u64>() {
            Ok(v) => v,
            Err(_) => 0,
        },
        Err(_) => 0,
    };
    let filesize = match extract_from_form(&entries, "size") {
        Ok(v) => match v.parse::<u64>() {
            Ok(v) => v,
            Err(_) => 0,
        },
        Err(_) => 0,
    };

    let path = match build_path(uid, created, modified, &bucket) {
        Ok(p) => p,
        Err(_) => {
            internal_error(request, "Could not build target path");
            return;
        }
    };

    let path2 = format!("{}", &path);
    let filename2 = format!("{}", &filename);
    let (tx, rx) = oneshot::channel::<Result<u64, i8>>();
    thread::spawn(move || {
        if path2.ends_with(".dng") {
            let _ = tx.send(Ok(0));
        } else {
            let id = database::add_media(
                path2,
                filename2,
                bucket,
                created,
                modified,
                lat,
                lon,
                h_res,
                v_res,
                duration,
                filesize,
                uid,
            );
            let _ = tx.send(id);
        }
    });

    let mut fullpath = String::new();
    fullpath.push_str(&path);
    fullpath.push_str(&filename);

    error = match create_dirs(&path, uid) {
        Ok(_) => false,
        Err(_) => true,
    };

    let file = match extract_from_form(&entries, "file") {
        Ok(f) => f,
        Err(_) => {
            internal_error(request, "Could not read file");
            let id: u64 = match rx.wait() {
                Ok(r) => match r {
                    Ok(i) => i,
                    Err(_) => {
                        common::log_error(
                            &"network.rs",
                            &"mediaupload_multipart",
                            line!(),
                            &"Could not add to database",
                        );
                        return;
                    }
                },
                Err(_) => {
                    common::log_error(
                        &"network.rs",
                        &"mediaupload_multipart",
                        line!(),
                        &"Could not unwrap channel",
                    );
                    return;
                }
            };
            let _ = database::remove_by_id(id);
            return;
        }
    };
    let _ = move_file(file, &fullpath, &CopyOptions::new());

    let f: File = match File::open(Path::new(&fullpath)) {
        Ok(v) => v,
        Err(_) => {
            error = true;
            match File::open("/tmp/dummy") {
                Ok(f) => f,
                Err(e) => {
                    internal_error(request, &format!("Could not open dummy file: {}", e));
                    return;
                }
            }
        }
    };

    if !error {
        match f.metadata() {
            Err(_) => {
                let _ = fs::remove_file(format!("{}", &fullpath));
                error = true;
            }
            Ok(v) => {
                if v.len() != filesize {
                    match fs::remove_file(format!("{}", &fullpath)) {
                        Ok(_) => {}
                        Err(_) => {}
                    };
                    error = true
                }
            }
        };
    }

    let _ = thread::spawn(move || {
        let (user, group, visible) = match database::ownership_info(uid) {
            Ok(i) => i,
            Err(_) => {
                common::log_error(
                    &"network.rs",
                    &"mediaupload_multipart",
                    line!(),
                    &"Could not get ownership info",
                );
                ("root".to_string(), "users".to_string(), true)
            }
        };
        if !common::change_owner(&fullpath, &user, &group, visible) {
            common::log_error(
                &"network.rs",
                &"mediaupload_multipart",
                line!(),
                &"Could not set ownership info",
            );
        }
    });

    if error {
        internal_error(request, "Error creating file");
        return;
    }

    let id: u64 = match rx.wait() {
        Ok(r) => match r {
            Ok(i) => i,
            Err(_) => {
                internal_error(
                    request,
                    "Error adding file to database, it was created successfully though",
                );
                common::log_error(
                    &"network.rs",
                    &"mediaupload_multipart",
                    line!(),
                    &"Could not add to database",
                );
                return;
            }
        },
        Err(_) => {
            internal_error(
                        request,
                        "We're not shure if the file was added file to database, it was created successfully though",
                    );
            common::log_error(
                &"network.rs",
                &"mediaupload_multipart",
                line!(),
                &"Could not unwrap channel",
            );
            return;
        }
    };

    maintenance::add_id(id);
    let response = http::Response::from_string(id.to_string());
    let _ = request.respond(response);
}

fn extract_from_form(entries: &Entries, key: &str) -> Result<String, i8> {
    let data = match entries.fields.get(&Arc::new(String::from(key))) {
        Some(d) => d,
        None => {
            common::log_error(
                &"network.rs",
                &"extract_from_form",
                line!(),
                &"Could not get data",
            );
            return Err(-1);
        }
    };
    for i in 0..data.len() {
        match data[i].data {
            SavedData::Text(ref string) => {
                return Ok(format!("{}", string));
            }
            SavedData::File(ref path_buf, _) => {
                return match path_buf.as_path().to_str() {
                    Some(s) => Ok(format!("{}", s)),
                    None => {
                        continue;
                    }
                }
            }
            _ => {
                continue;
            }
        }
    }
    return Err(-2);
}

pub fn mediaupload(mut request: http::Request, uid: u64) {
    let mut bucket = String::new();
    let mut filename = String::new();
    let lat: f64;
    let lon: f64;
    let created: u64;
    let modified: u64;
    let duration: i64;
    let h_res: u64;
    let v_res: u64;
    let filesize: u64;
    let mut error: bool = false;
    let path;
    let (tx, rx) = oneshot::channel::<Result<u64, i8>>();
    let mut fullpath = String::new();

    let dummy_file: File = match File::open("/tmp/dummy") {
        Ok(f) => f,
        Err(e) => {
            internal_error(request, &format!("Could not open dummy file: {}", e));
            return;
        }
    };
    let mut f: File = match File::open("/tmp/dummy") {
        Ok(f) => f,
        Err(e) => {
            internal_error(request, &format!("Could not open dummy file: {}", e));
            return;
        }
    };
    {
        let mut line = String::new();
        let mut reader = BufReader::new(request.as_reader());
        let _ = reader.read_line(&mut filename);
        filename = sanitize(filename);
        let _ = reader.read_line(&mut bucket);
        bucket = sanitize(bucket);
        let _ = reader.read_line(&mut line);
        line = sanitize(line);
        lat = match line.parse::<f64>() {
            Ok(v) => v,
            Err(_) => 0.0,
        };
        line = String::new();
        let _ = reader.read_line(&mut line);
        line = sanitize(line);
        lon = match line.parse::<f64>() {
            Ok(v) => v,
            Err(_) => 0.0,
        };
        line = String::new();
        let _ = reader.read_line(&mut line);
        line = sanitize(line);
        created = match line.parse::<u64>() {
            Ok(d) => d,
            Err(_) => {
                error = true;
                0
            }
        };
        line = String::new();
        let _ = reader.read_line(&mut line);
        line = sanitize(line);
        modified = match line.parse::<u64>() {
            Ok(d) => d,
            Err(_) => {
                error = true;
                0
            }
        };
        line = String::new();
        let _ = reader.read_line(&mut line);
        line = sanitize(line);
        h_res = match line.parse::<u64>() {
            Ok(d) => d,
            Err(_) => {
                error = true;
                0
            }
        };
        line = String::new();
        let _ = reader.read_line(&mut line);
        line = sanitize(line);
        v_res = match line.parse::<u64>() {
            Ok(d) => d,
            Err(_) => {
                error = true;
                0
            }
        };
        line = String::new();
        let _ = reader.read_line(&mut line);
        line = sanitize(line);
        duration = match line.parse::<i64>() {
            Ok(d) => d,
            Err(_) => {
                error = true;
                -1
            }
        };
        line = String::new();
        let _ = reader.read_line(&mut line);
        line = sanitize(line);
        filesize = match line.parse::<u64>() {
            Ok(d) => d,
            Err(_) => {
                error = true;
                0
            }
        };

        path = match build_path(uid, created, modified, &bucket) {
            Ok(p) => p,
            Err(_) => {
                error = true;
                String::new()
            }
        };

        if !error {
            let path2 = format!("{}", &path);
            let filename2 = format!("{}", &filename);
            thread::spawn(move || {
                if path2.ends_with(".dng") {
                    let _ = tx.send(Ok(0));
                } else {
                    let id = database::add_media(
                        path2,
                        filename2,
                        bucket,
                        created,
                        modified,
                        lat,
                        lon,
                        h_res,
                        v_res,
                        duration,
                        filesize,
                        uid,
                    );
                    let _ = tx.send(id);
                }
            });

            fullpath.push_str(&path);
            fullpath.push_str(&filename);

            error = match create_dirs(&path, uid) {
                Ok(_) => false,
                Err(_) => true,
            };

            f = match File::create(Path::new(&fullpath)) {
                Ok(v) => v,
                Err(_) => {
                    error = true;
                    dummy_file
                }
            };
        }
        if !error {
            let mut buf: [u8; 32 * 1024] = [0; 32 * 1024];
            loop {
                let length = match reader.read(&mut buf) {
                    Ok(n) => n,
                    Err(_) => buf.len() + 1,
                };
                if length == 0 {
                    break;
                }
                if length > buf.len() {
                    let mut f_name_copy = String::new(); //need to copy, remove_file moves fullpath
                    f_name_copy.push_str(&fullpath);
                    let _ = fs::remove_file(f_name_copy);
                    error = true;
                    break;
                }
                if !error {
                    match f.write_all(&buf[0..length]) {
                        Err(_) => {
                            let mut f_name_copy = String::new(); //need to copy, remove_file moves fullpath
                            f_name_copy.push_str(&fullpath);
                            let _ = fs::remove_file(f_name_copy);
                            error = true;
                            break;
                        }
                        Ok(_) => {
                            continue;
                        }
                    };
                }
            }
        }
    }
    if error {
        internal_error(request, "Could not build target path");
        return;
    }

    match f.metadata() {
        Err(_) => {
            let _ = fs::remove_file(format!("{}", &fullpath));
            error = true;
        }
        Ok(v) => {
            if v.len() != filesize {
                match fs::remove_file(format!("{}", &fullpath)) {
                    Ok(_) => {}
                    Err(_) => {}
                };
                error = true
            }
        }
    };

    let _ = thread::spawn(move || {
        let (user, group, visible) = match database::ownership_info(uid) {
            Ok(t) => t,
            Err(_) => {
                common::log_error(
                    &"network.rs",
                    &"mediaupload_multipart",
                    line!(),
                    &"Could not get ownership info",
                );
                ("root".to_string(), "users".to_string(), true)
            }
        };
        common::change_owner(&fullpath, &user, &group, visible);
    });

    if error {
        internal_error(request, "Error creating file");
        return;
    }

    let id: u64 = match rx.wait() {
        Ok(r) => match r {
            Ok(i) => i,
            Err(_) => {
                internal_error(
                    request,
                    "Error adding file to database, it was created successfully though",
                );
                common::log_error(
                    &"network.rs",
                    &"mediaupload_multipart",
                    line!(),
                    &"Could not add to database",
                );
                return;
            }
        },
        Err(_) => {
            internal_error(
                        request,
                        "We're not shure if the file was added file to database, it was created successfully though",
                    );
            common::log_error(
                &"network.rs",
                &"mediaupload_multipart",
                line!(),
                &"Could not unwrap channel",
            );
            return;
        }
    };

    maintenance::add_id(id);
    let response = http::Response::from_string(id.to_string());
    let _ = request.respond(response);
}

fn create_dirs(path: &str, uid: u64) -> Result<String, i8> {
    let p = Path::new(&path);
    let mut userpath = match database::get_userpath(uid) {
        Ok(s) => String::from(s),
        Err(_) => {
            return Err(-1);
        }
    };
    if !userpath.ends_with('/') {
        userpath.push('/');
    }
    let user_p = Path::new(&path);
    let user_p_exists = user_p.exists();
    if !p.exists() {
        match fs::create_dir_all(Path::new(&path)) {
            Err(_) => {
                return Err(-2);
            }
            Ok(_) => {
                //nothing
            }
        };
        let mut tmp_path: String = format!("{}", &path);
        let _ = thread::spawn(move || {
            if !tmp_path.ends_with('/') {
                tmp_path.push('/');
            }
            let (user, group, visible) = match database::ownership_info(uid) {
                Ok(t) => t,
                Err(_) => {
                    common::log_error(
                        &"network.rs",
                        &"mediaupload_multipart",
                        line!(),
                        &"Could not get ownership info",
                    );
                    ("root".to_string(), "users".to_string(), true)
                }
            };

            while tmp_path.len() > userpath.len() && tmp_path != "/" {
                common::change_owner(&tmp_path, &user, &group, visible);
                let pathcopy = format!("{}", &tmp_path);
                let tmp: Vec<&str> = pathcopy.rsplitn(3, '/').collect();
                tmp_path = format!("{}", tmp[2]);
                if !tmp_path.ends_with('/') {
                    tmp_path.push('/');
                }
            }
            if !user_p_exists {
                common::change_owner(&userpath, &user, &group, visible);
            }
        });
    }
    Ok(String::from(path))
}

fn build_path(uid: u64, created: u64, modified: u64, bucket: &str) -> Result<String, i8> {
    let mut path = match database::get_userpath(uid) {
        Ok(p) => p,
        Err(_) => {
            return Err(-1);
        }
    };
    if !path.ends_with('/') {
        path.push('/');
    }
    if created > 0 {
        path.push_str(&build_path_date(created, bucket));
    } else {
        path.push_str(&build_path_date(modified, bucket));
    }
    if !path.ends_with('/') {
        path.push('/');
    }
    Ok(path)
}

fn sanitize(string: String) -> String {
    let mut out = string;
    out = String::from(out.trim());
    let mut len = out.len();
    while out.ends_with("\n") {
        let _ = out.split_off(len - 1);
        len -= 1;
    }
    while out.ends_with("\r") {
        let _ = out.split_off(len - 1);
        len -= 1;
    }
    while out.ends_with("\\n") {
        let _ = out.split_off(len - 2);
        len -= 2;
    }
    while out.ends_with("\\r") {
        let _ = out.split_off(len - 2);
        len -= 2;
    }
    out
}

fn build_path_date(created: u64, bucket: &str) -> String {
    let date: DateTime<Local> =
        Local.timestamp((created / 1000) as i64, ((created % 1000) * 1000000) as u32);
    let year = date.year();
    let month = date.month();
    let mut month_text;
    let locale = common::get_locale();
    let key: String = format!("{}{}", locale, month);
    month_text = match common::get_string(&key) {
        Ok(s) => s,
        Err(_) => String::new(),
    };
    if month_text == String::new() && locale.len() > 2 {
        let (first, _) = locale.split_at(2);
        let key: String = format!("{}{}", first, month);
        month_text = match common::get_string(&key) {
            Ok(s) => s,
            Err(_) => String::new(),
        };
    }
    let mut month_nr = String::new();
    if month < 10 {
        month_nr.push('0');
    }
    month_nr.push_str(&month.to_string());
    format!("{}/{} {}/{}/", year, month_nr, month_text, bucket)
}

pub fn get_lastsync(request: http::Request, uid: u64) {
    let lastsync = match database::get_lastsync(uid) {
        Ok(s) => s,
        Err(_) => {
            internal_error(request, "Could not get lastsync from database");
            return;
        }
    };
    let response = http::Response::from_string(format!("{}", lastsync).to_string());
    let _ = request.respond(response);
}

pub fn set_lastsync(mut request: http::Request, uid: u64) {
    let lastsync: u64;
    let mut read_string = String::new();
    {
        let mut reader = BufReader::new(request.as_reader());
        let _ = reader.read_line(&mut read_string);
    }
    lastsync = match read_string.parse::<u64>() {
        Ok(o) => o,
        Err(_) => {
            internal_error(request, "Error parsing lastsync to u64");
            return;
        }
    };
    match database::set_lastsync(uid, lastsync) {
        Ok(_) => {
            //nothing
        }
        Err(_) => {
            internal_error(request, "Could not set lastsync");
            return;
        }
    }
    let response = http::Response::from_string(String::from("Success"));
    let _ = request.respond(response);
}

pub fn mediasearch(request: http::Request, uid: u64) {
    let _ = uid + 1;
    internal_error(request, "Feature not implemented yet");
}

pub fn internal_error(request: http::Request, message: &str) {
    println!(
        "Internal Error on URL: {} with message: {}",
        request.url(),
        message
    );
    let response =
        http::Response::from_string(message).with_status_code(http::StatusCode::from(500));
    let _ = request.respond(response);
}

pub fn gone(request: http::Request) {
    let response = http::Response::from_string("Object not found, it is probably gone")
        .with_status_code(http::StatusCode::from(410));
    let _ = request.respond(response);
}

pub fn url_not_found(request: http::Request) {
    let response =
        http::Response::from_string("URL not found").with_status_code(http::StatusCode::from(404));
    let _ = request.respond(response);
}
