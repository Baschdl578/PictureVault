extern crate ascii;
extern crate base64;
extern crate chrono;
extern crate fs_extra;
extern crate futures;
extern crate image;
#[macro_use]
extern crate lazy_static;
extern crate multipart;
extern crate mysql;
extern crate num_cpus;
extern crate quick_xml;
extern crate rand;
extern crate reqwest;
extern crate rexiv2;
extern crate time;
extern crate tiny_http;
extern crate two_lock_queue as tlq;

mod media;
mod database;
mod common;
mod network;
mod maintenance;

use std::sync::Arc;
use std::thread;

fn main() {
    database::init();
    maintenance::init();
    let addr = format!(
        "{}:{}",
        common::get_string("server_ip"),
        common::get_string("server_port")
    );
    let server = match tiny_http::Server::http(&addr) {
        Ok(rq) => rq,
        Err(e) => {
            println!("error: {}", e);
            return;
        }
    };
    let server = Arc::new(server);
    let cpus = num_cpus::get();
    let mut guards = Vec::with_capacity(cpus);

    for _ in 0..cpus {
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
        println!("Server thread spawned for {}", addr);
    }
    for g in guards {
        let _ = g.join();
    }
}
