use tiny_http as http;
use base64;
use chrono::prelude::{DateTime, Local};
use chrono::{Datelike, TimeZone};
use ascii::AsciiString;
use futures::Future;
use futures::sync::oneshot;
use multipart::server::{Entries, Multipart, SaveResult};
use multipart::server::save::{PartialReason, SaveDir, SavedData, TempDir};
use fs_extra::file::{move_file, CopyOptions};

use std::fs::{self, File};
use std::path::Path;
use std::io::{BufRead, BufReader, Read, Write};
use std::io::Seek;
use std::io::SeekFrom;
use std::str::FromStr;
use std::thread;
use std::sync::Arc;
use std::error::Error;

use database;
use maintenance;
use common;

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
                let bytes = base64::decode(&coded_value).unwrap();
                let value = String::from_utf8(bytes).unwrap();
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
    let uid = database::get_user_id_and_verify(&user, &pass);
    if uid <= 0 {
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

fn get_media_intern(uid: i64, media_id: i64, request: http::Request, range: u64) {
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

    let mut file = File::open(path).unwrap();

    let length = file.metadata().unwrap().len();

    let mut status = http::StatusCode(200);
    let mut response;
    if range > 0 {
        status = http::StatusCode(206);
    }
    if range >= length {
        status = http::StatusCode(416);
    }

    if range > 0 {
        file.seek(SeekFrom::Start(range)).unwrap();
    }

    response = http::Response::new(
        status,
        Vec::with_capacity(0),
        file,
        Some(length as usize - range as usize),
        None,
    );

    let header = http::Header {
        field: http::HeaderField::from_str("Content-Range").unwrap(),
        value: AsciiString::from_str(&format!("bytes {}-{}", range, length)).unwrap(),
    };
    response.add_header(header);

    let header = http::Header {
        field: http::HeaderField::from_str("Accept-Ranges").unwrap(),
        value: AsciiString::from_str("none").unwrap(),
    };
    response.add_header(header);

    let _ = request.respond(response);
}

pub fn get_media_url(url: String, uid: i64, request: http::Request, range: u64) {
    let string = String::from(url.trim_matches('/'));
    let values: Vec<&str> = string.split("/").collect::<Vec<&str>>();
    let mut id_str = String::new();
    if values.len() > 2 {
        id_str.push_str(values[2]);
    } else {
        internal_error(request, String::from("Could not read media id"));
        return;
    }
    let id = match id_str.parse::<i64>() {
        Ok(n) => n,
        Err(_) => {
            internal_error(request, String::from("Could not read media id"));
            return;
        }
    };
    get_media_intern(uid, id, request, range);
}

pub fn stream_media(url: String, uid: i64, request: http::Request) {
    let string = String::from(url.trim_matches('/'));
    let values: Vec<&str> = string.split("/").collect::<Vec<&str>>();
    let mut id_str = String::new();
    if values.len() > 3 {
        id_str.push_str(values[2]);
    } else {
        internal_error(request, String::from("Could not read media id"));
        return;
    }
    let id = match id_str.parse::<i64>() {
        Ok(n) => n,
        Err(_) => {
            internal_error(request, String::from("Could not read media id"));
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
        let mpd_path = pic.get_mpd_path();
        let path = Path::new(&mpd_path);
        let out = match File::open(path) {
            Ok(v) => v,
            Err(_) => {
                //Retry once
                let tmp = match File::open(path) {
                    Ok(v) => v,
                    Err(_) => {
                        internal_error(
                            request,
                            format!("Could not open file: {}", &mpd_path).to_string(),
                        );
                        return;
                    }
                };
                tmp
            }
        };
        let response = http::Response::from_file(out);
        let _ = request.respond(response);
    } else {
        pic.prepare_for_streaming();
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
                        internal_error(
                            request,
                            format!("Could not open file: {}", &filepath).to_string(),
                        );
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

pub fn get_media_stream(mut request: http::Request, uid: i64, range: u64) {
    let mut id_str: String = String::new();
    {
        let mut reader = BufReader::new(request.as_reader());
        let _ = reader.read_line(&mut id_str);
    }
    let id = match id_str.parse::<i64>() {
        Ok(n) => n,
        Err(_) => {
            internal_error(request, String::from("Could not read picture id"));
            return;
        }
    };
    get_media_intern(uid, id, request, range);
}

pub fn get_libs(request: http::Request, uid: i64) {
    let libs = database::get_libs(uid);
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
        out.push(';');
        out.push_str(&thumb1.to_string());
        out.push(';');
        out.push_str(&thumb2.to_string());
        out.push(';');
        out.push_str(&thumb3.to_string());
        out.push(';');
        out.push_str(&thumb4.to_string());
    }

    let response = http::Response::from_string(out);
    let _ = request.respond(response);
}

pub fn get_lib_mediaids(mut request: http::Request, uid: i64) {
    let mut id_str = String::new();
    {
        let mut reader = BufReader::new(request.as_reader());
        let _ = reader.read_line(&mut id_str);
    }
    let id = match id_str.parse::<i64>() {
        Ok(n) => n,
        Err(_) => {
            internal_error(request, String::from("Could not read library id"));
            return;
        }
    };
    let ids = database::get_pics_by_lib(id, uid);
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

pub fn get_mediainfo(mut request: http::Request, uid: i64) {
    let mut id: String = String::new();
    let media;
    {
        let mut reader = BufReader::new(request.as_reader());
        let _ = reader.read_line(&mut id);
    }
    let pic_id = match id.parse::<i64>() {
        Ok(n) => n,
        Err(_) => {
            internal_error(request, String::from("Could not read picture id"));
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

pub fn get_mediathumb(mut request: http::Request, uid: i64) {
    let mut picpath = String::new();
    let path;
    let mut id: String = String::new();
    {
        let mut reader = BufReader::new(request.as_reader());
        let _ = reader.read_line(&mut id);
    }
    let pic_id = match id.parse::<i64>() {
        Ok(n) => n,
        Err(_) => {
            internal_error(request, String::from("Could not read picture id"));
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
    picpath.push_str(&pic.get_thumbnail(true));
    path = Path::new(&picpath);
    let out = match File::open(path) {
        Ok(v) => v,
        Err(_) => {
            //Retry once
            let tmp = match File::open(path) {
                Ok(v) => v,
                Err(_) => {
                    internal_error(request, String::from("Could not open file"));
                    return;
                }
            };
            tmp
        }
    };
    let response = http::Response::from_file(out);
    let _ = request.respond(response);
}

fn mediaupload_multipart(mut request: http::Request, uid: i64) {
    let tmp_path = database::get_userpath(uid);
    create_dirs(&tmp_path, uid);
    let tmp_dir = TempDir::new_in(&tmp_path, ".tmp").unwrap();

    let mut entries = Entries::new(SaveDir::Temp(TempDir::new(&tmp_path).unwrap()));

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
        internal_error(request, String::from("Request is not multipart"));
        return;
    }
    if error {
        internal_error(request, message);
        return;
    }

    let bucket = extract_from_form(&entries, "bucket");
    let filename = extract_from_form(&entries, "filename");
    let created = match extract_from_form(&entries, "created").parse::<u64>() {
        Ok(v) => v,
        Err(_) => 0,
    };
    let modified = match extract_from_form(&entries, "modified").parse::<u64>() {
        Ok(v) => v,
        Err(_) => 0,
    };
    let lat = match extract_from_form(&entries, "latitude").parse::<f64>() {
        Ok(v) => v,
        Err(_) => 0.0,
    };
    let lon = match extract_from_form(&entries, "longitude").parse::<f64>() {
        Ok(v) => v,
        Err(_) => 0.0,
    };
    let duration = match extract_from_form(&entries, "duration").parse::<i64>() {
        Ok(v) => v,
        Err(_) => -1,
    };
    let h_res = match extract_from_form(&entries, "horizontal resolution").parse::<u64>() {
        Ok(v) => v,
        Err(_) => 0,
    };
    let v_res = match extract_from_form(&entries, "vertical resolution").parse::<u64>() {
        Ok(v) => v,
        Err(_) => 0,
    };
    let filesize = match extract_from_form(&entries, "size").parse::<u64>() {
        Ok(v) => v,
        Err(_) => 0,
    };

    let path = build_path(uid, created, modified, &bucket);

    let path2 = format!("{}", &path);
    let filename2 = format!("{}", &filename);
    let (tx, rx) = oneshot::channel::<i64>();
    thread::spawn(move || {
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
            uid,
        );
        let _ = tx.send(id);
    });

    let mut fullpath = String::new();
    fullpath.push_str(&path);
    fullpath.push_str(&filename);

    error = create_dirs(&path, uid);

    let _ = move_file(
        extract_from_form(&entries, "file"),
        &fullpath,
        &CopyOptions::new(),
    );

    let f: File = match File::open(Path::new(&fullpath)) {
        Ok(v) => v,
        Err(_) => {
            error = true;
            File::open("/tmp/dummy").unwrap()
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
        let (user, group, visible) = database::ownership_info(uid);
        common::change_owner(&fullpath, &user, &group, visible);
    });

    if error {
        internal_error(request, String::from("Error creating file"));
        return;
    }

    let id: i64 = rx.wait().unwrap();

    if id < 0 {
        internal_error(
            request,
            String::from("Error adding file to database, it was created successfully though"),
        );
        return;
    }

    maintenance::add_id(id);
    let response = http::Response::from_string(id.to_string());
    let _ = request.respond(response);
}

fn extract_from_form(entries: &Entries, key: &str) -> String {
    let data = entries.fields.get(&Arc::new(String::from(key))).unwrap();
    for i in 0..data.len() {
        match data[i].data {
            SavedData::Text(ref string) => {
                return format!("{}", string);
            }
            SavedData::File(ref path_buf, _) => {
                return format!("{}", path_buf.as_path().to_str().unwrap());
            }
            _ => {
                continue;
            }
        }
    }
    return String::new();
}

pub fn mediaupload(mut request: http::Request, uid: i64) {
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
    let mut error: bool;
    let path;
    let (tx, rx) = oneshot::channel::<i64>();
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
        created = line.parse::<u64>().unwrap();
        line = String::new();
        let _ = reader.read_line(&mut line);
        line = sanitize(line);
        modified = line.parse::<u64>().unwrap();
        line = String::new();
        let _ = reader.read_line(&mut line);
        line = sanitize(line);
        h_res = line.parse::<u64>().unwrap();
        line = String::new();
        let _ = reader.read_line(&mut line);
        line = sanitize(line);
        v_res = line.parse::<u64>().unwrap();
        line = String::new();
        let _ = reader.read_line(&mut line);
        line = sanitize(line);
        duration = line.parse::<i64>().unwrap();
        line = String::new();
        let _ = reader.read_line(&mut line);
        line = sanitize(line);
        filesize = line.parse::<u64>().unwrap();

        path = build_path(uid, created, modified, &bucket);

        let path2 = format!("{}", &path);
        let filename2 = format!("{}", &filename);
        thread::spawn(move || {
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
                uid,
            );
            let _ = tx.send(id);
        });

        let mut fullpath = String::new();
        fullpath.push_str(&path);
        fullpath.push_str(&filename);

        error = create_dirs(&path, uid);

        let mut f: File = match File::create(Path::new(&fullpath)) {
            Ok(v) => v,
            Err(_) => {
                error = true;
                File::open("/tmp/dummy").unwrap()
            }
        };
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
            let (user, group, visible) = database::ownership_info(uid);
            common::change_owner(&fullpath, &user, &group, visible);
        });
    }

    if error {
        internal_error(request, String::from("Error creating file"));
        return;
    }

    let id: i64 = rx.wait().unwrap();

    if id < 0 {
        internal_error(
            request,
            String::from("Error adding file to database, it was created successfully though"),
        );
        return;
    }

    maintenance::add_id(id);
    let response = http::Response::from_string(id.to_string());
    let _ = request.respond(response);
}

fn create_dirs(path: &str, uid: i64) -> bool {
    let mut error = false;
    let p = Path::new(&path);
    let mut userpath = String::from(database::get_userpath(uid));
    if userpath.ends_with('/') {
        userpath.push('/');
    }
    let user_p = Path::new(&path);
    let user_p_exists = user_p.exists();
    if !p.exists() {
        match fs::create_dir_all(Path::new(&path)) {
            Err(_) => error = true,
            Ok(_) => {}
        };
        let mut tmp_path: String = format!("{}", &path);
        let _ = thread::spawn(move || {
            if !tmp_path.ends_with('/') {
                tmp_path.push('/');
            }
            let (user, group, visible) = database::ownership_info(uid);

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
    error
}

fn build_path(uid: i64, created: u64, modified: u64, bucket: &str) -> String {
    let mut path = database::get_userpath(uid);
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
    path
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
    month_text = common::get_string(&key);
    if month_text == String::new() && locale.len() > 2 {
        let (first, _) = locale.split_at(2);
        let key: String = format!("{}{}", first, month);
        month_text = common::get_string(&key);
    }
    let mut month_nr = String::new();
    if month < 10 {
        month_nr.push('0');
    }
    month_nr.push_str(&month.to_string());
    format!("{}/{} {}/{}/", year, month_nr, month_text, bucket)
}

pub fn get_lastsync(request: http::Request, uid: i64) {
    let response =
        http::Response::from_string(String::from(database::get_lastsync(uid).to_string()));
    let _ = request.respond(response);
}

pub fn set_lastsync(mut request: http::Request, uid: i64) {
    let lastsync: u64;
    let mut read_string = String::new();
    {
        let mut reader = BufReader::new(request.as_reader());
        let _ = reader.read_line(&mut read_string);
    }
    lastsync = match read_string.parse::<u64>() {
        Ok(o) => o,
        Err(_) => {
            internal_error(request, String::from("Error parsing lastsync to u64"));
            return;
        }
    };
    database::set_lastsync(uid, lastsync);
    let response = http::Response::from_string(String::from("Success"));
    let _ = request.respond(response);
}

pub fn mediasearch(request: http::Request, uid: i64) {
    let _ = uid + 1;
    internal_error(request, String::from("Feature not implemented yet"));
}

pub fn internal_error(request: http::Request, message: String) {
    println!(
        "Internal Error on URL: {} with message: {}",
        request.url(),
        &message
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
