extern crate ascii;
extern crate base64;
extern crate chrono;
extern crate fs_extra;
extern crate futures;
extern crate image;
#[macro_use]
extern crate lazy_static;
#[macro_use]
extern crate log;
extern crate mysql;
extern crate num_cpus;
extern crate quick_xml;
extern crate rand;
extern crate reqwest;
extern crate rexiv2;
extern crate simple_logging;
extern crate time;
extern crate actix;
extern crate actix_web;
extern crate two_lock_queue as tlq;
#[macro_use]
extern crate serde_derive;
extern crate serde;
extern crate form_data;
extern crate mime;
extern crate openssl;
extern crate http;

mod common;
mod database;
mod maintenance;
mod media;
mod structs;
mod handlers;

use std::process::exit;

use actix_web::{server::HttpServer, App, http::Method};
use log::LevelFilter;
use openssl::ssl::{SslMethod, SslAcceptor, SslFiletype};

fn main() {
    let _ = simple_logging::log_to_file("/var/log/picture_vault", LevelFilter::Error);

    match database::init() {
        Err(_) => {
            exit(5);
        }
        _ => {
            //nothing
        }
    }
    match maintenance::init() {
        Err(_) => {
            exit(5);
        }
        _ => {
            //nothing
        }
    }
    let ip = match common::get_string("server_ip") {
        Ok(s) => s,
        Err(_) => {
            common::log_error(&"main.rs", &"main", line!(), "Could not get server ip");
            exit(2);
        }
    };

    let port = match common::get_string("server_port") {
        Ok(s) => s,
        Err(_) => {
            common::log_error(&"main.rs", &"main", line!(), "Could not get server port");
            exit(2);
        }
    };

    let addr = format!("{}:{}", ip, port);

    let mut do_ssl = true;


    let cert = match common::get_string("cert") {
        Ok(v) => v,
        Err(_) => {
            do_ssl = false;
            String::new()
        },
    };
    let pkey = match common::get_string("private_key") {
        Ok(v) => v,
        Err(_) => {
            do_ssl = false;
            String::new()
        }
    };

    if cert.len() == 0 || pkey.len() == 0 {
        do_ssl = false;
    }

    let mut builder = SslAcceptor::mozilla_intermediate(SslMethod::tls()).unwrap();
    builder.set_private_key_file(&pkey, SslFiletype::PEM).unwrap();
    builder.set_certificate_chain_file(&cert).unwrap();
    
    let sys = actix::System::new("example");


    let server = HttpServer::new(|| {
        App::new()
            .resource("/media/load/{id}/{name}", |r| {
                r.method(Method::POST).f(handlers::get_media_url)
            }) /*
            .resource("/media/stream/{id}/{name}", |r| {
                r.method(Method::POST).f(handlers::stream_media)
            }) */
            .resource("/pulse", |r| r.method(Method::POST).f(handlers::echo))
            .resource("/library/all", |r| {
                r.method(Method::POST).f(handlers::get_libs)
            })
            .resource("/library/media", |r| {
                r.method(Method::POST).f(handlers::get_lib_mediaids)
            })
            .resource("/media/thumb", |r| {
                r.method(Method::POST).f(handlers::get_mediathumb)
            })
            .resource("/media/info", |r| {
                r.method(Method::POST).f(handlers::get_mediainfo)
            })
            .resource("/media/load", |r| {
                r.method(Method::POST).f(handlers::get_media)
            })
            .resource("/media/upload", |r| {
                r.method(Method::POST).f(handlers::media_upload)
            })
            .resource("/media/search", |r| {
                r.method(Method::POST).f(handlers::media_search)
            })
            .resource("/lastsync/get", |r| {
                r.method(Method::POST).f(handlers::get_lastsync)
            })
            .resource("/lastsync/set", |r| {
                r.method(Method::POST).f(handlers::set_lastsync)
            })
    });
    if do_ssl {
        let mut builder = SslAcceptor::mozilla_intermediate(SslMethod::tls()).unwrap();
        builder.set_private_key_file(&pkey, SslFiletype::PEM).unwrap();
        builder.set_certificate_chain_file(&cert).unwrap();
        server.bind_ssl(addr, builder).unwrap().start();
    } else {
        server.bind(addr).unwrap().start();
    }

    let _ = sys.run();
}
