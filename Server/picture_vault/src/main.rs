extern crate mysql;
extern crate image;
extern crate rexiv2;
#[macro_use]
extern crate lazy_static;
extern crate base64;
extern crate rand;
extern crate two_lock_queue as tlq;
extern crate num_cpus;
extern crate time;
extern crate quick_xml;
extern crate chrono;
extern crate ascii;
extern crate actix_web;

use actix_web::{HttpServer, Application};

mod media;
mod database;
mod common;
mod network;
//mod maintenance;

fn main() {
    //database::init();
    //maintenance::init();
    let addr = format!(
        "{}:{}",
        common::get_string("server_ip"),
        common::get_string("server_port")
    );
    let server = HttpServer::new(
        || Application::new()
            .resource("/{any}", |r| r.f(network::answer)))
        .bind("127.0.0.1:8080").unwrap()
        .run();


}
