use tlq;
use num_cpus;
use mysql as sql;
use quick_xml::reader::Reader;
use quick_xml::events::Event;
use reqwest;

use std::io::Read;
use std::sync::Mutex;
use std::time as stdtime;
use std::thread;

use common;
use database;

struct ProdCons {
    tx: tlq::Sender<i64>,
    rx: tlq::Receiver<i64>,
}

lazy_static! {
    static ref PROD_CONS : Mutex<ProdCons> = {
        let (a, b) = tlq::channel::<i64>(10240);
        let out = ProdCons {
            tx: a,
            rx: b,
        };
        Mutex::new(out)
    };
    static ref LAST_GEOCODE : Mutex<u64> = Mutex::new(0);
}


pub fn init() {
    let cpus = num_cpus::get();
    for _ in 0..cpus {
        let rx = PROD_CONS.lock().unwrap().rx.clone();
        thread::spawn(move || loop {
            let id = rx.recv().unwrap();
            make_thumbnail(id);
            do_geocode(id);
        });
    }

    thread::spawn(move || loop {
        let pool = database::get_db();
        let query = database::build_query("SELECT id FROM §§.Media WHERE doneCoding = ?");
        let mut stmt = pool.prepare(query).unwrap();
        let result = stmt.execute((false,)).unwrap();
        for wrapped_row in result {
            let row = match wrapped_row {
                Err(_) => {
                    common::log(&"maintenance.rs", &"init", &"Error unwraping row");
                    continue;
                }
                Ok(row) => row,
            };
            let (id,) = match sql::from_row_opt::<(i64,)>(row) {
                Ok(e) => e,
                Err(_) => {
                    continue;
                }
            };
            add_id(id);
        }
        let pool = database::get_db();
        let query = database::build_query(
            "SELECT id FROM §§.Media WHERE last_request < ? AND duration >= 0",
        );
        let mut stmt = pool.prepare(query).unwrap();
        let time = common::current_time_millis() - (24 * 3600 * 1000);
        let result = stmt.execute((time,)).unwrap();
        for wrapped_row in result {
            let row = match wrapped_row {
                Err(_) => {
                    common::log(&"maintenance.rs", &"init", &"Error unwraping row");
                    continue;
                }
                Ok(row) => row,
            };
            let (id,) = match sql::from_row_opt::<(i64,)>(row) {
                Ok(e) => e,
                Err(_) => {
                    continue;
                }
            };
            let media = match database::get_mediainfo_by_id(id) {
                Ok(v) => v,
                Err(_) => {
                    return;
                }
            };
            media.cleanup();
        }




        thread::sleep(stdtime::Duration::from_secs(12 * 3600));
    });
}


pub fn add_id(id: i64) {
    PROD_CONS.lock().unwrap().tx.clone().send(id).unwrap();
}


fn make_thumbnail(id: i64) {
    let media = match database::get_mediainfo_by_id(id) {
        Ok(v) => v,
        Err(_) => {
            return;
        }
    };
    let _ = media.get_thumbnail(false);
}



fn do_geocode(id: i64) {
    let pool = database::get_db();
    let mut query =
        database::build_query("SELECT latitude, longitude FROM §§.Media WHERE id = ?;");
    let mut stmt = pool.prepare(query).unwrap();
    let result = stmt.execute((id,)).unwrap();

    for wrapped_row in result {
        let row = match wrapped_row {
            Err(_) => {
                common::log(&"maintenance.rs", &"do_geocode", &"Error unwraping row");
                continue;
            }
            Ok(row) => row,
        };
        let (lat, long) = match sql::from_row_opt::<(f64, f64)>(row) {
            Ok(e) => e,
            Err(_) => {
                continue;
            }
        };
        if lat == 0.0 || long == 0.0 {
            continue;
        }
        let mut url = String::new();
        url.push_str(
            "https://nominatim.openstreetmap.org/reverse?format=xml&lat=",
        );
        url.push_str(&lat.to_string());
        url.push_str("&lon=");
        url.push_str(&long.to_string());
        url.push_str("&zoom=18&addressdetails=1&accept-language=");
        url.push_str(&common::get_locale());

        let mut response = String::new();
        {
            let mut last_time = LAST_GEOCODE.lock().unwrap();
            let now = common::current_time_millis();
            let wait_time: u64 = 5 * 1000;

            if *last_time + wait_time > now {
                thread::sleep(stdtime::Duration::from_millis(*last_time + wait_time - now))
            }

            let mut resp = reqwest::get(&url).unwrap();
            let _ = resp.read_to_string(&mut response);

            *last_time = common::current_time_millis();
        }

        let mut reader = Reader::from_str(&response);
        reader.trim_text(true);

        let mut buf = Vec::new();
        let mut found = false;
        // The `Reader` does not implement `Iterator` because it outputs borrowed data (`Cow`s)
        loop {
            match reader.read_event(&mut buf) {
                Ok(Event::Start(ref e)) => {
                    match e.name() {
                        b"suburb" => {
                            found = true;
                        }
                        b"city" => {
                            found = true;
                        }
                        b"county" => {
                            found = true;
                        }
                        b"town" => {
                            found = true;
                        }
                        b"village" => {
                            found = true;
                        }
                        b"state" => {
                            found = true;
                        }
                        b"neighbourhood" => {
                            found = true;
                        }
                        b"country" => {
                            found = true;
                        }
                        b"state_district" => {
                            found = true;
                        }
                        _ => (),
                    }
                }
                Ok(Event::Text(e)) => {
                    if found {
                        let pool = database::get_db();
                        let query = database::build_query(
                            "INSERT IGNORE INTO §§.Places (picture, place) VALUES (?, ?);",
                        );
                        let mut stmt = pool.prepare(query).unwrap();
                        let _ = stmt.execute((id, e.unescape_and_decode(&reader).unwrap()))
                            .unwrap();
                        found = false;
                    }
                }
                Ok(Event::Eof) => break, // exits the loop when reaching end of file
                _ => (), // There are several other `Event`s we do not consider here
            }

            // if we don't keep a borrow elsewhere, we can clear the buffer to keep memory usage low
            buf.clear();
        }
        query = database::build_query("UPDATE §§.Media SET doneCoding = ? WHERE id = ?;");
        let mut stmt = pool.prepare(query).unwrap();
        let _ = stmt.execute((true, id)).unwrap();
    }
}
