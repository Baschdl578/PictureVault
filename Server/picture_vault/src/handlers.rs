use actix_web::{Responder, FutureResponse, fs::NamedFile, http::ContentEncoding, http::HeaderMap, HttpRequest, HttpResponse, HttpMessage};
use base64;
use chrono::prelude::{DateTime, Local};
use chrono::{Datelike, TimeZone};
use futures::{Future, future::ok};
use form_data::{Error, Field, Form, Value, FilenameGenerator, handle_multipart};
use mime;
use fs_extra::file::{move_file, CopyOptions};
use http::StatusCode;

use std::fs;
use std::path::{Path, PathBuf};
use std::thread;

use common;
use database;
use maintenance;
use structs;
use structs::MyResponse;

lazy_static! {
    static ref upload_form: Form = Form::new()
        .field("Bucket", Field::text())
        .field("Filename", Field::text())
        .field("Latitude", Field::float())
        .field("Longitude", Field::float())
        .field("Created", Field::int())
        .field("Modified", Field::int())
        .field("h_res", Field::int())
        .field("v_res", Field::int())
        .field("Size", Field::int())
        .field("Duration", Field::int());
}

pub fn echo(req: &HttpRequest) -> HttpResponse {
    let payload = req.request().payload();
    HttpResponse::Ok()
        .content_encoding(ContentEncoding::Identity)
        .content_type("application/octet-stream")
        .streaming(payload)
}

fn get_media_intern(uid: u64, media_id: u64) -> MyResponse {
    let mut picpath = String::new();
    let path;
    let pic = match database::get_mediainfo(uid, media_id) {
        Ok(p) => p,
        Err(_) => {
            return MyResponse::new_response(gone());
        }
    };
    picpath.push_str(&pic.get_full_path());
    path = Path::new(&picpath);

    match NamedFile::open(path) {
        Ok(f) => {return MyResponse::new_file(f);},
        Err(_) => {
            return MyResponse::new_response(internal_error("Could not open file"));
        }
    }
}

pub fn get_media_url(req: &HttpRequest) -> MyResponse {
    let uid = match get_uid(req.headers()) {
        Ok(i) => i,
        Err(_) => {
            return MyResponse::new_response(bad_login());
        }
    };
    let id_str = match req.match_info().get("name") {
        Some(s) => s,
        None => {
            return MyResponse::new_response(internal_error("Could not read media id"));
        }
    };
    let id = match id_str.parse::<u64>() {
        Ok(n) => n,
        Err(_) => {
            return MyResponse::new_response(internal_error("Could not parse media id"));
        }
    };
    return get_media_intern(uid, id);
}
/*
pub fn load_mpd(req: &HttpRequest) -> HttpResponse {
    let uid = match get_uid(req.headers()) {
        Ok(i) => i,
        Err(_) => {
            return bad_login();
        }
    };
    let id_str = match req.match_info().get("name") {
        Some(s) => s,
        None => {
            return internal_error("Could not read media id");
        }
    };
    let id = match id_str.parse::<u64>() {
        Ok(n) => n,
        Err(_) => {
            return internal_error("Could not parse media id");
        }
    };
    let pic = match database::get_mediainfo(uid, id) {
        Ok(p) => p,
        Err(_) => {
            return gone();
        }
    };

        let mpd_path = match pic.get_mpd_path() {
            Ok(p) => p,
            Err(_) => {
                return internal_error("Could not load mpd path");
            }
        };
        let path = Path::new(&mpd_path);
        let out = match NamedFile::open(path) {
            Ok(v) => v,
            Err(_) => {
                //Retry once
                let tmp = match NamedFile::open(path) {
                    Ok(v) => v,
                    Err(_) => {
                        return internal_error(&format!("Could not open file: {}", &mpd_path));
                    }
                };
                tmp
            }
        };
        match out.respond_to(req) {
            Ok(r)   => { return r; },
            Err(_)  => { return internal_error("Could not respond with file"); }
        }
} */
/*
pub fn stream_file(req: &HttpRequest) -> HttpResponse {
    let uid = match get_uid(req.headers()) {
        Ok(i) => i,
        Err(_) => {
            return bad_login();
        }
    };
    let id_str = match req.match_info().get("name") {
        Some(s) => s,
        None => {
            return internal_error("Could not read media id");
        }
    };
    let id = match id_str.parse::<u64>() {
        Ok(n) => n,
        Err(_) => {
            return internal_error("Could not parse media id");
        }
    };
    let pic = match database::get_mediainfo(uid, id) {
        Ok(p) => p,
        Err(_) => {
            return gone();
        }
    };

    let _ = pic.prepare_for_streaming();
        let mut filepath = pic.get_streaming_path();
        if !filepath.ends_with("/") {
            filepath.push('/');
        }
        //filepath.push_str(values[3]);
        let path = Path::new(&filepath);
        let out = match NamedFile::open(path) {
            Ok(v) => v,
            Err(_) => {
                //Retry once
                let tmp = match NamedFile::open(path) {
                    Ok(v) => v,
                    Err(_) => {
                        return internal_error(&format!("Could not open file: {}", &filepath));
                    }
                };
                tmp
            }
        };
        match out.respond_to(req) {
            Ok(r)   => { return r; },
            Err(_)  => { return internal_error("Could not respond with file"); }
        }
}*/

pub fn get_media(req: &HttpRequest) -> FutureResponse<impl Responder> {
    let uid = match get_uid(req.headers()) {
        Ok(i) => i,
        Err(_) => {
            return Box::new(ok(MyResponse::new_response(bad_login())));
        }
    };

    Box::new(req.urlencoded::<structs::Id>()
        .from_err()
        .and_then(move |data| {
            return ok(get_media_intern(uid, data.id));
        }))
}

pub fn get_libs(req: &HttpRequest) -> HttpResponse {
    let uid = match get_uid(req.headers()) {
        Ok(i) => i,
        Err(_) => {
            return bad_login();
        }
    };
    let libs = match database::get_libs(uid) {
        Ok(l) => l,
        Err(_) => {
            return internal_error("Could not load libraries from database");
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

    return HttpResponse::Ok()
        .content_encoding(ContentEncoding::Identity)
        .content_type("application/octet-stream")
        .body(out);
}

pub fn get_lib_mediaids(req: &HttpRequest) -> FutureResponse<HttpResponse> {
    let uid = match get_uid(req.headers()) {
        Ok(i) => i,
        Err(_) => {
            return Box::new(ok(bad_login()));
        }
    };

    Box::new(req.urlencoded::<structs::Id>()
        .from_err()
        .and_then(move |data| {
            let ids = match database::get_pics_by_lib(data.id, uid) {
                Ok(i) => i,
                Err(_) => {
                    return ok(internal_error("Could not load library pics from database"));
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

            return ok(HttpResponse::Ok()
                .content_encoding(ContentEncoding::Identity)
                .content_type("application/octet-stream")
                .body(out));
        }))
    
}

pub fn get_mediainfo(req: &HttpRequest) -> FutureResponse<HttpResponse> {
    let uid = match get_uid(req.headers()) {
        Ok(i) => i,
        Err(_) => {
            return Box::new(ok(bad_login()));
        }
    };
    Box::new(req.urlencoded::<structs::Id>()
        .from_err()
        .and_then(move |data| {
            let media = match database::get_mediainfo(uid, data.id) {
                Ok(p) => p,
                Err(_) => {
                    return ok(gone());
                }
            };

            return ok(HttpResponse::Ok()
                .content_encoding(ContentEncoding::Identity)
                .content_type("text/plain")
                .body(media.to_string()));
        }))
}

pub fn get_mediathumb(req: &HttpRequest) -> FutureResponse<MyResponse> {
    let uid = match get_uid(req.headers()) {
        Ok(i) => i,
        Err(_) => {
            return Box::new(ok(MyResponse::new_response(bad_login())));
        }
    };

    Box::new(req.urlencoded::<structs::Id>()
        .from_err()
        .and_then(move |data| {
            let pic = match database::get_mediainfo(uid, data.id) {
                Ok(p) => p,
                Err(_) => {
                    return ok(MyResponse::new_response(gone()));
                }
            };
            let thumbpath = match pic.get_thumbnail(true) {
                Ok(s) => s,
                Err(_) => {
                    return ok(MyResponse::new_response(internal_error("Could not get thumbnail path")));
                }
            };
            let path = Path::new(&thumbpath);
            let out = match NamedFile::open(path) {
                Ok(v) => v,
                Err(_) => {
                    //Retry once
                    let tmp = match NamedFile::open(path) {
                        Ok(v) => v,
                        Err(e) => {
                            return ok(MyResponse::new_response(internal_error(&format!("Could not open file {}", e))));
                        }
                    };
                    tmp
                }
            };
            return ok(MyResponse::new_file(out));
    }))
}


pub fn media_upload(req: &HttpRequest) -> Box<Future<Item = HttpResponse, Error = Error>> {
    let uid = match get_uid(req.headers()) {
        Ok(i) => i,
        Err(_) => {
            return Box::new(ok(bad_login()));
        }
    };

    let basepath = match database::get_userpath(uid) {
            Ok(p)   => p,
            Err(_)  => "/tmp".to_owned()
    };
    match fs::create_dir_all(Path::new(&format!("{}/.temp/", basepath))) {
        Ok(_)   => {
            //nothing
        },
        Err(_)  => {return Box::new(ok(internal_error("Could not create temp file")));}
    }
    let temp_path = format!("{}/.temp/{}", basepath, common::current_time_millis());
    let temp_path2 = format!("{}", &temp_path);
    let name_generator = Gen::new(temp_path2);
    

    let form = upload_form.clone().field("File", Field::file(name_generator));
    //return Box::new(ok(gone()));

    Box::new(handle_multipart(req.request().multipart(), form)
        .map(move |uploaded_content| {
            let mut bucket = String::new();
            let created: u64;
            let modified: u64;
            let latitude: f64;
            let longitude: f64;
            let h_res: u64;
            let v_res: u64;
            let size: u64;
            let duration: i64;
            let filename: String; 
            match uploaded_content {
                Value::Map(mut hashmap) => {
                    match hashmap.remove("Bucket") {
                        Some(value) => {
                            match value {
                            Value::Text(value) => {bucket = value;},
                            _ => {
                                return internal_error("Could not read bucket");
                            },
                            }
                        },
                        None => {
                            internal_error("Could not read bucket");
                        },
                    }
                    match hashmap.remove("Created") {
                        Some(value) => {
                            match value {
                            Value::Int(value) => {created = value as u64;},
                            _ => {
                                return internal_error("Could not read created timestamp");
                            },
                            }
                        }
                        None => {
                                return internal_error("Could not read created timestamp");
                            },
                    }
                    match hashmap.remove("Modified") {
                        Some(value) => {match value {
                            Value::Int(value) => {modified = value as u64;},
                            _ => {
                                return internal_error("Could not read modified timestamp");
                            },
                            }
                        }
                        None => {
                                return internal_error("Could not read modified timestamp");
                            },
                    }
                    match hashmap.remove("Latitude") {
                        Some(value) => {match value {
                            Value::Float(value) => {latitude = value;},
                            _ => {
                                return internal_error("Could not read latitude");
                            },
                        }
                        }
                        None => {
                                return internal_error("Could not read latitude");
                            },
                    }
                    match hashmap.remove("Longitude") {
                        Some(value) => {match value {
                            Value::Float(value) => {longitude = value;},
                            _ => {
                                return internal_error("Could not read longitude");
                            },
                        }
                        }
                        None => {
                                return internal_error("Could not read longitude");
                            },
                    }
                    match hashmap.remove("h_res") {
                        Some(value) => {match value {
                            Value::Int(value) => {h_res = value as u64;},
                            _ => {
                                return internal_error("Could not read horizontal resolution");
                            },
                        }
                        }
                        None => {
                                return internal_error("Could not read horizontal resolution");
                            },
                    }
                    match hashmap.remove("v_res") {
                        Some(value) => {match value {
                            Value::Int(value) => {v_res = value as u64;},
                            _ => {
                                return internal_error("Could not read vertical resolution");
                            },
                        }
                        }
                        None => {
                                return internal_error("Could not read vertical resolution");
                            },
                    }
                    match hashmap.remove("Size") {
                        Some(value) => {match value {
                            Value::Int(value) => {size = value as u64;},
                            _ => {
                                return internal_error("Could not read filesize");
                            },
                        }
                        }
                        None => {
                                return internal_error("Could not read filesize");
                            },
                    }
                    match hashmap.remove("Duration") {
                        Some(value) => {match value {
                            Value::Int(value) => {duration = value;},
                            _ => {
                                return internal_error("Could not read video duration");
                            },
                        }
                        }
                        None => {
                                return internal_error("Could not read video duration");
                            },
                    }
                    match hashmap.remove("Filename") {
                        Some(value) => {match value {
                            Value::Text(value) => {filename = value;},
                            _ => {
                                return internal_error("Could not read filename");
                            },
                        }
                        }
                        None => {
                                return internal_error("Could not read filename");
                            },
                    }
                    
                },
                _ => {
                                return internal_error("Could not read multipart request");
                            },
            }
            let mut dir_path = match build_path(uid, created, modified, &bucket) {
                Ok(p) => p,
                Err(_) => {
                    common::log_error(
                        "handlers.rs",
                        "mediaupload",
                        line!(),
                        &format!(
                            "Could not build path: {}, {}, {}, {}",
                            uid, created, modified, &bucket
                        ),
                    );
                    let _ = fs::remove_file(&format!("{}", &temp_path));
                    return internal_error("Could not build path for file");
                }
            };
            if dir_path.ends_with("/") {
                dir_path.push('/');
            }

            
                match create_dirs(&dir_path, uid) {
                    Ok(_)   => {},
                    Err(_)  => {
                        common::log_error(
                        "handlers.rs",
                        "media_upload",
                        line!(),
                        "Could not create directories",
                    );
                        return internal_error("Could not create directories");
                    },
                }
                let file_path = format!("{}{}", &dir_path, &filename);
                                let file_path2 = format!("{}", &file_path);

                match move_file(temp_path, file_path2, &CopyOptions::new()) {
                    Ok(_)   => {},
                    Err(e)  => {
                        common::log_error(
                        "handlers.rs",
                        "media_upload",
                        line!(),
                        "Could not move file",
                    );
                        return internal_error(&format!("Could not move file: {}", e));
                    },
                }

            let _ = thread::spawn(move || {
                let (user, group, visible) = match database::ownership_info(uid) {
                    Ok(t) => t,
                    Err(_) => {
                        common::log_error(
                            "handlers.rs",
                            "mediaupload",
                            line!(),
                            "Could not get ownership info",
                        );
                        ("root".to_string(), "users".to_string(), true)
                    }
                };
                common::change_owner(&file_path, &user, &group, visible);
            });

            if !filename.ends_with(".dng") {
                let id = match database::add_media(
                        dir_path, filename, bucket, created, modified, latitude, longitude, h_res, v_res,
                        duration, size, uid
                    ) {
                        Ok(i)   => i,
                        Err(_)  => {
                            common::log_error(
                                "handlers.rs",
                                "media_upload",
                                line!(),
                                "Could not add file to database",
                            );
                            return internal_error(
                                "Error adding file to database, it was created successfully though");
                        }
                };
                maintenance::add_id(id);
                return HttpResponse::Ok()
                    .content_encoding(ContentEncoding::Identity)
                    .content_type("text/plain")
                    .body(id.to_string());

            }
            return HttpResponse::Ok()
                    .content_encoding(ContentEncoding::Identity)
                    .content_type("text/plain")
                    .body("-1");

        }))
/*
    Box::new(req.urlencoded::<structs::MediaInfo>()
        .from_err()
        .and_then(|data| {
            if !error {
            path = match build_path(uid, data.created, data.modified, &data.bucket) {
                Ok(p) => p,
                Err(_) => {
                    common::log_error(
                        "handlers.rs",
                        "mediaupload",
                        line!(),
                        &format!(
                            "Could not build path: {}, {}, {}, {}",
                            uid, data.created, data.modified, &data.bucket
                        ),
                    );
                    error = true;
                    String::new()
                }
            };

            let path2 = format!("{}", &path);
            let filename2 = format!("{}", &data.filename);
            thread::spawn(move || {
                if path2.ends_with(".dng") {
                    let _ = tx.send(Ok(0));
                } else {
                    let id = database::add_media(
                        path2, filename2, data.bucket, data.created, data.modified, data.latitude, data.longitude, data.h_res, data.v_res,
                        data.duration, data.size, uid, 0
                    );
                    let _ = tx.send(id);
                }
            });

            fullpath.push_str(&path);
            fullpath.push_str(&data.filename);
        }

        if !error {
            error = match create_dirs(&path, uid) {
                Ok(_) => false,
                Err(_) => {
                    common::log_error(
                        "handlers.rs",
                        "mediaupload",
                        line!(),
                        "Could not create directories",
                    );
                    true
                }
            };
        }
        if !error {
            f = match File::create(Path::new(&fullpath)) {
                Ok(v) => v,
                Err(e) => {
                    common::log_error(
                        "handlers.rs",
                        "mediaupload",
                        line!(),
                        &format!("Could not create file: {}", e),
                    );
                    error = true;
                    dummy_file
                }
            };
        }

        if error {
        return ok(internal_error("Error creating file"));
    }

    let _ = thread::spawn(move || {
        let (user, group, visible) = match database::ownership_info(uid) {
            Ok(t) => t,
            Err(_) => {
                common::log_error(
                    "handlers.rs",
                    "mediaupload",
                    line!(),
                    "Could not get ownership info",
                );
                ("root".to_string(), "users".to_string(), true)
            }
        };
        common::change_owner(&fullpath, &user, &group, visible);
    });

    

    let id: u64 = match rx.wait() {
        Ok(r) => match r {
            Ok(i) => i,
            Err(_) => {
                common::log_error(
                    &"handlers.rs",
                    &"mediaupload_multipart",
                    line!(),
                    &"Could not add to database",
                );
                return ok(internal_error(
                    "Error adding file to database, it was created successfully though",
                ));
            }
        },
        Err(_) => {
            common::log_error(
                &"handlers.rs",
                &"mediaupload_multipart",
                line!(),
                &"Could not unwrap channel",
            );
            return ok(internal_error(
                        "We're not sure if the file was added file to database, it was created successfully though",
                    ));
        }
    };

    maintenance::add_id(id);
    return ok(HttpResponse::Ok()
        .content_encoding(ContentEncoding::Identity)
        .content_type("text/plain")
        .body(id.to_string()));
    }))
    */
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
                        &"handlers.rs",
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
            common::log_error(
                "handlers.rs",
                "build_path",
                line!(),
                "Could not get userpath",
            );
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

/*
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
} */

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

pub fn get_lastsync(req: &HttpRequest) -> HttpResponse {
    let uid = match get_uid(req.headers()) {
        Ok(i) => i,
        Err(_) => {
            return bad_login();
        }
    };
    let lastsync = match database::get_lastsync(uid) {
        Ok(s) => s,
        Err(_) => {
            return internal_error("Could not get lastsync from database");
        }
    };

    return HttpResponse::Ok()
        .content_encoding(ContentEncoding::Identity)
        .content_type("text/plain")
        .body(lastsync.to_string());
}

pub fn set_lastsync(req: &HttpRequest) -> FutureResponse<HttpResponse> {
    let uid = match get_uid(req.headers()) {
        Ok(i) => i,
        Err(_) => {
            return Box::new(ok(bad_login()));
        }
    };

    Box::new(req.urlencoded::<structs::LastSync>()
        .from_err()
        .and_then(move |data| {
            match database::set_lastsync(uid, data.time) {
                Ok(_) => {
                    //nothing
                }
                Err(_) => {
                    return ok(internal_error("Could not set lastsync"));
                }
            }
            return ok(HttpResponse::Ok()
                .content_encoding(ContentEncoding::Identity)
                .content_type("text/plain")
                .body("Success"));
    }))
    
}

pub fn media_search(_req: &HttpRequest) -> HttpResponse {
    //let _ = uid + 1;
    return internal_error("Feature not implemented yet");
}

pub fn internal_error(message: &str) -> HttpResponse {
    common::log_error(
        "handlers.rs",
        "internal_error",
        line!(),
        &format!("Internal server error: {}", message),
    );
    return HttpResponse::build(StatusCode::INTERNAL_SERVER_ERROR)
        .content_encoding(ContentEncoding::Identity)
        .content_type("text/plain")
        .body(message.to_owned());
}

pub fn gone() -> HttpResponse {
    return HttpResponse::Gone()
        .content_encoding(ContentEncoding::Identity)
        .content_type("text/plain")
        .body("Object not found, it is probably gone");
}
/*
pub fn url_not_found() -> HttpResponse {
    return HttpResponse::NotFound()
        .content_encoding(ContentEncoding::Identity)
        .content_type("text/plain")
        .body("URL not found");
} */

fn bad_login() -> HttpResponse {
    HttpResponse::Unauthorized()
        .content_encoding(ContentEncoding::Identity)
        .content_type("text/plain")
        .body("Bad login")
}

fn get_uid(header: &HeaderMap) -> Result<u64, i8> {
    match header.get("Authorization") {
        None => {
            return Err(-1);
        }
        Some(v) => {
            let mut coded_value = String::new();
            let mut coded_value1 = String::new();
            let header_value = match v.to_str() {
                Ok(s)   => s,
                Err(_)  => { return Err(-4); }
            };
            coded_value1.push_str(header_value);
            if coded_value1.starts_with("Basic ") {
                coded_value = coded_value1.split_off(6);
            }
            let bytes = match base64::decode(&coded_value) {
                Ok(s) => s,
                Err(_) => {
                    return Err(-1);
                }
            };
            let value = match String::from_utf8(bytes) {
                Ok(s) => s,
                Err(_) => {
                    return Err(-1);
                }
            };
            let split: Vec<&str> = value.split(":").collect();

            match database::get_user_id_and_verify(split[0], split[1]) {
                Ok(i) => {
                    return Ok(i);
                }
                Err(-5) => {
                    return Err(-2);
                }
                Err(_) => {
                    return Err(-3);
                }
            };
        }
    };
}
/*
fn get_range(header: &HeaderMap) -> u64 {
    match header.get("Range") {
        None => return 0,
        Some(v) => {
            let mut coded_value = String::new();
            let mut coded_value1 = String::new();
            let header_value = match v.to_str() {
                Ok(s)   => s,
                Err(_)  => { return 0; }
            };
            coded_value1.push_str(header_value);
            if coded_value1.starts_with("bytes=") {
                coded_value = coded_value1.split_off(6);
            }
            if coded_value.ends_with("-") {
                let length = coded_value.len();
                let _ = coded_value.split_off(length - 1);
            } else {
                let split: Vec<String> = coded_value.split("-").map(|s| s.to_string()).collect();
                coded_value = String::new();
                coded_value.push_str(&split[0]);
            }
            let range = match coded_value.parse::<u64>() {
                Ok(n) => n,
                Err(_) => 0,
            };
            return range;
        }
    };
} */


struct Gen {
    path: String,
}

impl FilenameGenerator for Gen {
    fn next_filename(&self, _: &mime::Mime) -> Option<PathBuf> {
        let mut buf = PathBuf::new();
        buf.push(&self.path);
        Some(buf)
    }
}

impl Gen {
    fn new(path: String) -> Gen {
        Gen {
            path: path,
        }
    }
}
