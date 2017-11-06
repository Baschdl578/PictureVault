extern crate mysql;
extern crate image;
extern crate rexiv2;
#[macro_use]
extern crate lazy_static;
extern crate tiny_http;
extern crate base64;
extern crate rand;
extern crate two_lock_queue as tlq;
extern crate num_cpus;
extern crate time;
extern crate quick_xml;
extern crate reqwest;
extern crate chrono;
extern crate ascii;

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
    let addr = format!("{}:{}", common::get_string("server_ip"), common::get_string("server_port"));
    let server = match tiny_http::Server::http(&addr) {
        Ok(rq) => rq,
        Err(e) => {
            println!("error: {}", e);
            return;
        }
    };
    let server = Arc::new(server);
    let mut guards = Vec::with_capacity(4);

    for _ in 0..4 {
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
