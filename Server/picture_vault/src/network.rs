use base64;
use chrono::prelude::{DateTime, Local};
use chrono::{TimeZone, Datelike};
use ascii::AsciiString;
use actix_web::*;

use std::fs::{self, File};
use std::path::Path;
use std::io::{self, BufReader, BufRead, Read, Write};
use std::io::Seek;
use std::io::SeekFrom;
use std::str::FromStr;
use std::thread;
use std::collections::HashMap;

use database;
//use maintenance;
use common;

pub fn answer(req: HttpRequest) -> Result<HttpResponse> {
    println!("Got url {}", req.uri());
    Ok(HttpResponse::Ok()
       .content_type("text/plain")
       .body(format!("Hello {}!", req.match_info().get("any").unwrap()))?)

    /*
    if req.path() == "/test/echo" {
        return echo(req)
    }
    let mut user = String::new();
    let mut pass = String::new();
    let mut range: u64 = 0;
    let mut auth_available = false;
    {
        let headers = req.headers();

        auth_available = headers.has::<Authorization<header::Basic>>();
        if auth_available {
            let auth = headers.get::<Authorization<header::Basic>>().unwrap();
            user = String::from(format!("{}", &auth.username));
            pass = match &auth.password {
                &Some(ref s) => String::from(format!("{}", &s)),
                _   => String::new(),
            };
        }
    }

    let uid = database::get_user_id_and_verify(&user, &pass);
    if uid <= 0 || !auth_available {
        let mut message = String::from("Bad Login: ");
        message.push_str(&user);
        message.push_str(" with password: ");
        message.push_str(&pass);
        message.push_str(&format!(" and URL: {}", req.path()));
        println!("{}", &message);
        let mut response = Response::new();
        response.set_body(message);
        response.set_status(hyper::StatusCode::Unauthorized);
        return Box::new(futures::future::ok(response))
    }

    if req.path().starts_with("/media/load/") {
        return get_media_url(String::from(req.path()), uid, req)
    }
    if req.path().starts_with("/media/stream/") {
        return stream_media(String::from(req.path()), uid, req)
    } */
/*
    match req.path() {
        "/test/echo" => {
            return echo(req);
        }
        "/library/all" => {
            return get_libs(req, uid);
        }
        "/library/media" => {
            return get_lib_mediaids(req, uid);
        }
        "/media/thumb" => {
            return get_mediathumb(req, uid);
        }
        "/media/info" => {
            return get_mediainfo(req, uid);
        }
        "/media/load" => {
            return get_media_stream(req, uid);
        }
        "/media/upload" => {
            return mediaupload(req, uid);
        }
        "/media/upload/ios" => {
            return mediaupload_form(req, uid);
        }
        "/media/search" => {
            return mediasearch(req, uid);
        }
        "/lastsync/get" => {
            return get_lastsync(req, uid);
        }
        "/lastsync/set" => {
            return set_lastsync(req, uid);
        }
        "/pulse" => {
            return echo(req);
        }
        _ => {
            return url_not_found(req);
        }
    } */
    //return url_not_found(req);
} /*

pub fn echo(req: Request) -> Box<Future<Item = Response, Error = hyper::Error>> {
    let mut response = Response::new();
    response.set_body(req.body());
    Box::new(futures::future::ok(response))
}

fn get_media_intern(uid: i64, media_id: i64, req: Request) -> Box<Future<Item = Response, Error = hyper::Error>> {
    let mut picpath = String::new();
    let pic = match database::get_mediainfo(uid, media_id) {
        Ok(p) => p,
        Err(_) => {
            return gone()
        }
    };
    picpath.push_str(&pic.get_full_path());

    response_from_file(&picpath)
}

fn response_from_file(path: &str) -> Box<Future<Item = Response, Error = hyper::Error>> {
    let (tx, rx) = oneshot::channel();
    let mypath = String::from(path);
    thread::spawn(move || {
	    let not_found: &[u8] = b"not found";
        let mut file = match File::open(mypath) {
            Ok(f) => f,
            Err(_) => {
                tx.send(Response::new()
                        .with_status(hyper::StatusCode::NotFound)
                        .with_header(ContentLength(not_found.len() as u64))
                        .with_body(not_found))
                    .expect("Send error on open");
                return;
            },
        };
        let (mut tx_body, rx_body) = mpsc::channel(1);
        let res = Response::new().with_body(rx_body);
        tx.send(res).expect("Send error on successful file read");

        let mut buf = [0u8; 4096];
        loop {
            match file.read(&mut buf) {
                Ok(n) => {
                    if n == 0 {
                        // eof
                        tx_body.close().expect("panic closing");
                        break;
                    } else {
                        let chunk: hyper::Chunk = buf.to_vec().into();
                        match tx_body.send(Ok(chunk)).wait() {
                            Ok(t) => { tx_body = t; },
                            Err(_) => { break; }
                        };
                    }
                },
                Err(_) => { break; }
            }
        }
    });
    Box::new(rx.map_err(|e| hyper::Error::from(io::Error::new(io::ErrorKind::Other, e))))
}

pub fn get_media_url(url: String, uid: i64, request: Request) -> Box<Future<Item = Response, Error = hyper::Error>> {
    let string = String::from(url.trim_matches('/'));
    let values: Vec<&str> = string.split("/").collect::<Vec<&str>>();
    let mut id_str = String::new();
    if values.len() > 2 {
        id_str.push_str(values[2]);
    } else {
        return internal_error(request, String::from("Could not read media id"))
    }
    let id = match id_str.parse::<i64>() {
        Ok(n) => n,
        Err(_) => {
            return internal_error(request, String::from("Could not read media id"))
        }
    };
    get_media_intern(uid, id, request)
}


pub fn stream_media(url: String, uid: i64, request: Request) -> Box<Future<Item = Response, Error = hyper::Error>> {
    let string = String::from(url.trim_matches('/'));
    let values: Vec<&str> = string.split("/").collect::<Vec<&str>>();
    let mut id_str = String::new();
    if values.len() > 3 {
        id_str.push_str(values[2]);
    } else {
        return internal_error(request, String::from("Could not read media id"));
    }
    let id = match id_str.parse::<i64>() {
        Ok(n) => n,
        Err(_) => {
            return internal_error(request, String::from("Could not read media id"));
        }
    };
    let pic = match database::get_mediainfo(uid, id) {
        Ok(p) => p,
        Err(_) => {
            return gone();
        }
    };

    if values[3] == "mpd" {
        let mpd_path = pic.get_mpd_path();
        return response_from_file(&mpd_path);
    } else {
        pic.prepare_for_streaming();
        let mut filepath = pic.get_streaming_path();
        if !filepath.ends_with("/") {
            filepath.push('/');
        }
        filepath.push_str(values[3]);
        return response_from_file(&filepath);
    }
}


pub fn get_media_stream(request: Request, uid: i64) -> Box<Future<Item = Response, Error = hyper::Error>> {
    let mut id_str: String = String::new();
    //let (_, _, _, headers, mut reader) = request.deconstruct();
    //let nodes = mime_multipart::read_multipart_body(&mut reader, &headers, false).unwrap();
    let multipart = request.into_multipart().unwrap();


    let id = match id_str.parse::<i64>() {
        Ok(n) => n,
        Err(_) => {
            return internal_error(request, String::from("Could not read picture id"));
        }
    };
    return get_media_intern(uid, id, request);
}
*/
/*
pub fn get_libs(request: Request, uid: i64) -> Box<Future<Item = Response, Error = hyper::Error>> {
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

pub fn get_lib_mediaids(mut request: http::Request, uid: i64) -> Box<Future<Item = Response, Error = hyper::Error>> {
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


pub fn get_mediainfo(mut request: http::Request, uid: i64) -> Box<Future<Item = Response, Error = hyper::Error>> {
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

pub fn get_mediathumb(mut request: http::Request, uid: i64) -> Box<Future<Item = Response, Error = hyper::Error>> {
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


pub fn mediaupload_form(mut request: http::Request, uid: i64) -> Box<Future<Item = Response, Error = hyper::Error>> {
    let bucket: String;
    let filename: String;
    let lat: f64;
    let lon: f64;
    let created: u64;
    let modified: u64;
    let duration: i64;
    let h_res: u64;
    let v_res: u64;
    let filesize: u64;
    let mut path = String::new();
    {
        let mut reader = request.as_reader();
        let form_data = formdata::read_formdata(&mut reader, &hyper::header::Headers::new()).unwrap();

        for (name, value) in form_data.fields {
            println!("Posted field name={} value={}", name, value);
        }

        for (name, file) in form_data.files {
            println!("Posted file name={} path={:?}", name, file.path);
        }
    }
}

pub fn mediaupload(mut request: http::Request, uid: i64) -> Box<Future<Item = Response, Error = hyper::Error>> {
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
    let mut error = false;
    let path;

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
        let mut fullpath = String::new();
        fullpath.push_str(&path);
        fullpath.push_str(&filename);

        let p = Path::new(&path);
        if !p.exists() {
            match fs::create_dir_all(Path::new(&path)) {
                Err(_) => error = true,
                Ok(_) => {}
            };
            let mut tmp_path: String = String::from(format!("{}", &path));
            let _ = thread::spawn(move || {
                if !tmp_path.ends_with('/') {
                    tmp_path.push('/');
                }
                let (user, group, visible) = database::ownership_info(uid);
                let mut userpath = String::from(database::get_userpath(uid));
                if userpath.ends_with('/') {
                    userpath.push('/');
                }
                while tmp_path.len() > userpath.len() {
                    common::change_owner(&tmp_path, &user, &group, visible);
                    let pathcopy = String::from(format!("{}", &tmp_path));
                    let tmp: Vec<&str> = pathcopy.rsplitn(3, '/').collect();
                    tmp_path = String::from(format!("{}", tmp[2]));
                    if !tmp_path.ends_with('/') {
                        tmp_path.push('/');
                    }
                }
            });
        }
        let mut f: File = match File::create(Path::new(&fullpath)) {
            Ok(v) => v,
            Err(_) => {
                error = true;
                File::open("/tmp/dummy").unwrap()
            }
        };
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

    let path = String::from(format!("{}", &path));
    let id = database::add_media(
        path,
        filename,
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

    if id < 0 {
        internal_error(
            request,
            String::from(
                "Error adding file to database, it was created successfully though",
            ),
        );
        return;
    }

    maintenance::add_id(id);
    let response = http::Response::from_string(id.to_string());
    let _ = request.respond(response);
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
    String::from(format!("{}/{} {}/{}/", year, month_nr, month_text, bucket))
}

pub fn get_lastsync(request: http::Request, uid: i64) -> Box<Future<Item = Response, Error = hyper::Error>> {
    let response =
        http::Response::from_string(String::from(database::get_lastsync(uid).to_string()));
    let _ = request.respond(response);
}

pub fn set_lastsync(mut request: http::Request, uid: i64) -> Box<Future<Item = Response, Error = hyper::Error>> {
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
    let response = http::Response::from_string(String::from("Successfully set"));
    let _ = request.respond(response);
}

pub fn mediasearch(request: http::Request, uid: i64) -> Box<Future<Item = Response, Error = hyper::Error>> {
    let _ = uid + 1;
    internal_error(request, String::from("Feature not implemented yet"));
}

pub fn internal_error(request: Request, message: String) -> Box<Future<Item = Response, Error = hyper::Error>> {
    println!(
        "Internal Error on URL: {} with message: {}",
        request.path(),
        &message
    );
    let mut response = Response::new();
    response.set_body(message);
    response.set_status(hyper::StatusCode::InternalServerError);

    Box::new(futures::future::ok(response))
}

pub fn gone() -> Box<Future<Item = Response, Error = hyper::Error>> {
    let mut response = Response::new();
    response.set_status(hyper::StatusCode::Gone);

    Box::new(futures::future::ok(response))
}

pub fn url_not_found(request: Request) -> Box<Future<Item = Response, Error = hyper::Error>> {
    let mut response = Response::new();
    response.set_body(format!("URL not found: {}", request.path()));
    response.set_status(hyper::StatusCode::NotFound);

    Box::new(futures::future::ok(response))
} */
