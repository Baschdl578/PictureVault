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
extern crate multipart;
extern crate mysql;
extern crate num_cpus;
extern crate quick_xml;
extern crate rand;
extern crate reqwest;
extern crate rexiv2;
extern crate simple_logging;
extern crate time;
extern crate tiny_http;
extern crate two_lock_queue as tlq;

mod common;
mod database;
mod maintenance;
mod media;
mod network;

use std::process::exit;
use std::sync::Arc;
use std::thread;

use log::LevelFilter;

fn main() {
    let _ = simple_logging::log_to_file("/var/log/picture_vault", LevelFilter::Info);

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

    /*
    let cert = match common::load_file(&common::get_string("cert")) {
        Ok(v) => v,
        Err(_) => Vec::new(),
    };
    let pkey = match common::load_file(&common::get_string("private_key")) {
        Ok(v) => v,
        Err(_) => Vec::new(),
    };
    let ssl_conf = tiny_http::SslConfig {
        certificate: cert,
        private_key: pkey,
    };

    let config = tiny_http::ServerConfig {
        addr: addr,
        ssl: Some(ssl_conf),
    };

    let server = match tiny_http::Server::new(config) {
        Ok(rq) => rq,
        Err(e) => {
            println!("error: {}", e);
            return;
        }
    }; */

    let server = match tiny_http::Server::http(&addr) {
        Ok(rq) => rq,
        Err(e) => {
            println!("error: {}", e);
            return;
        }
    };
    let server = Arc::new(server);
    let cpus = num_cpus::get();

    let mut loop_count = 1;
    loop {
        let mut guards = Vec::with_capacity(cpus);

        for i in 0..cpus {
            let server = server.clone();

            let guard = thread::spawn(move || loop {
                let request = match server.recv() {
                    Ok(rq) => rq,
                    Err(e) => {
                        println!("error: {}", e);
                        break;
                    }
                };
                network::answer(request);
            });
            guards.push(guard);
            println!("Server thread {} of {} spawned", i + 1, cpus);
        }
        for g in guards {
            let _ = g.join();
        }
        println!("All server threads died {} times", loop_count);
        loop_count += 1;
    }
}
