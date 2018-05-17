use mysql as sql;
use num_cpus;
use quick_xml::events::Event;
use quick_xml::reader::Reader;
use reqwest;
use tlq;

use std::io::Read;
use std::process::exit;
use std::sync::Mutex;
use std::thread;
use std::time as stdtime;

use common;
use database;

struct ProdCons {
    tx: tlq::Sender<u64>,
    rx: tlq::Receiver<u64>,
}

lazy_static! {
    static ref PROD_CONS: Mutex<ProdCons> = {
        let (a, b) = tlq::channel::<u64>(10240);
        let out = ProdCons { tx: a, rx: b };
        Mutex::new(out)
    };
    static ref LAST_GEOCODE: Mutex<u64> = Mutex::new(0);
}

pub fn init() -> Result<u8, i8> {
    let cpus = num_cpus::get();
    for _ in 0..cpus {
        let rx = match PROD_CONS.lock() {
            Ok(p) => p.rx.clone(),
            Err(e) => {
                common::log_error(
                    &"maintenance.rs",
                    &"init",
                    line!(),
                    &format!("Could not lock channel: {}", e),
                );
                exit(5);
            }
        };
        thread::spawn(move || loop {
            let id = match rx.recv() {
                Ok(i) => i,
                Err(_) => {
                    common::log_error(
                        &"maintenance.rs",
                        &"init",
                        line!(),
                        &"Could not get id from channel",
                    );
                    continue;
                }
            };
            match make_thumbnail(id) {
                Ok(_) => {
                    //nothing
                }
                Err(_) => {
                    continue;
                }
            };
            match do_geocode(id) {
                Ok(_) => {
                    //nothing
                }
                Err(_) => {
                    continue;
                }
            };
        });
    }

    let mut uneven = true;
    thread::spawn(move || loop {
        common::log_info(&"maintenance.rs", &"init", line!(), &"Running maintenance");
        let start = common::current_time_millis();

        if uneven {
            make_all_thumbnails();
            uneven = false;
        } else {
            uneven = true;
        }

        let pool = match database::get_db() {
            Ok(db) => db,
            Err(_) => {
                common::log_error(
                    &"maintenance.rs",
                    &"init",
                    line!(),
                    &"Could not get database",
                );
                continue;
            }
        };
        let query = match database::build_query("SELECT id FROM §§.Media WHERE doneCoding = ?") {
            Ok(q) => q,
            Err(_) => {
                common::log_error(
                    &"maintenance.rs",
                    &"init",
                    line!(),
                    &"Could not build query",
                );
                continue;
            }
        };
        let mut stmt = match pool.prepare(query) {
            Ok(s) => s,
            Err(e) => {
                common::log_error(
                    &"maintenance.rs",
                    &"init",
                    line!(),
                    &format!("Could not prepare statement: {}", e),
                );
                continue;
            }
        };
        let result = match stmt.execute((false,)) {
            Ok(s) => s,
            Err(e) => {
                common::log_error(
                    &"maintenance.rs",
                    &"init",
                    line!(),
                    &format!("Could not execute statement: {}", e),
                );
                continue;
            }
        };
        for wrapped_row in result {
            let row = match wrapped_row {
                Err(_) => {
                    common::log_error(&"maintenance.rs", &"init", line!(), &"Error unwraping row");
                    continue;
                }
                Ok(row) => row,
            };
            let (id,) = match sql::from_row_opt::<(u64,)>(row) {
                Ok(e) => e,
                Err(_) => {
                    continue;
                }
            };
            match make_thumbnail(id) {
                Ok(_) => {
                    //nothing
                }
                Err(_) => {
                    continue;
                }
            };
            match do_geocode(id) {
                Ok(_) => {
                    //nothing
                }
                Err(_) => {
                    continue;
                }
            };
        }
        let pool = match database::get_db() {
            Ok(p) => p,
            Err(_) => {
                common::log_error(
                    &"maintenance.rs",
                    &"init",
                    line!(),
                    &"Could not get database",
                );
                continue;
            }
        };
        let query = match database::build_query(
            "SELECT id FROM §§.Media WHERE last_request < ? AND duration >= 0",
        ) {
            Ok(q) => q,
            Err(_) => {
                common::log_error(
                    &"maintenance.rs",
                    &"init",
                    line!(),
                    &"Could not build database",
                );
                continue;
            }
        };
        let mut stmt = match pool.prepare(query) {
            Ok(s) => s,
            Err(e) => {
                common::log_error(
                    &"maintenance.rs",
                    &"init",
                    line!(),
                    &format!("Could not prepare statement: {}", e),
                );
                continue;
            }
        };
        let time = common::current_time_millis() - (24 * 3600 * 1000);
        let result = match stmt.execute((time,)) {
            Ok(s) => s,
            Err(e) => {
                common::log_error(
                    &"maintenance.rs",
                    &"init",
                    line!(),
                    &format!("Could not execute statement: {}", e),
                );
                continue;
            }
        };
        for wrapped_row in result {
            let row = match wrapped_row {
                Err(_) => {
                    common::log_error(&"maintenance.rs", &"init", line!(), &"Error unwraping row");
                    continue;
                }
                Ok(row) => row,
            };
            let (id,) = match sql::from_row_opt::<(u64,)>(row) {
                Ok(e) => e,
                Err(_) => {
                    continue;
                }
            };
            let media = match database::get_mediainfo_by_id(id) {
                Ok(v) => v,
                Err(_) => {
                    continue;;
                }
            };
            media.cleanup();
        }

        common::log_info(
            &"maintenance.rs",
            &"init",
            line!(),
            &format!(
                "Maintenance took: {}s",
                (common::current_time_millis() - start) / 1000,
            ),
        );

        thread::sleep(stdtime::Duration::from_secs(12 * 3600));
    });
    Ok(0)
}

pub fn add_id(id: u64) {
    match PROD_CONS.lock() {
        Ok(p) => {
            match p.tx.clone().send(id) {
                Ok(_) => {
                    //nothing
                }
                Err(e) => {
                    common::log_error(
                        &"maintenance.rs",
                        &"add_id",
                        line!(),
                        &format!("Could not add id: {}", e),
                    );
                }
            }
        }
        Err(e) => {
            common::log_error(
                &"maintenance.rs",
                &"add_id",
                line!(),
                &format!("Could not lock channel: {}", e),
            );
        }
    }
}

fn make_thumbnail(id: u64) -> Result<u8, i8> {
    let media = match database::get_mediainfo_by_id(id) {
        Ok(v) => v,
        Err(_) => {
            common::log_error(
                &"maintenance.rs",
                &"make_thumbnail",
                line!(),
                &"Could not get mediainfo",
            );
            return Err(-2);
        }
    };
    match media.get_thumbnail(false) {
        Ok(_) => {
            return Ok(0);
        }
        Err(_) => {
            return Err(-1);
        }
    }
}

fn do_geocode(id: u64) -> Result<u8, i8> {
    let pool = match database::get_db() {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"maintenance.rs",
                &"do_geocode",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let mut query =
        match database::build_query("SELECT latitude, longitude FROM §§.Media WHERE id = ?;") {
            Ok(q) => q,
            Err(_) => {
                common::log_error(
                    &"maintenance.rs",
                    &"do_geocode",
                    line!(),
                    &"Could not build query",
                );
                return Err(-1);
            }
        };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"maintenance.rs",
                &"do_gocode",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    let result = match stmt.execute((id,)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"maintenance.rs",
                &"do_geocode",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };

    for wrapped_row in result {
        let row = match wrapped_row {
            Err(_) => {
                common::log_error(
                    &"maintenance.rs",
                    &"do_geocode",
                    line!(),
                    &"Error unwraping row",
                );
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
        url.push_str("https://nominatim.openstreetmap.org/reverse?format=xml&lat=");
        url.push_str(&lat.to_string());
        url.push_str("&lon=");
        url.push_str(&long.to_string());
        url.push_str("&zoom=18&addressdetails=1&accept-language=");
        url.push_str(&common::get_locale());

        let mut response = String::new();
        wait_for_geocode();

        let mut resp = match reqwest::get(&url) {
            Ok(r) => r,
            Err(e) => {
                common::log_error(
                    &"maintenance.rs",
                    &"do_geocode",
                    line!(),
                    &format!("Could not fetch geocoding data: {}", e),
                );
                return Err(-7);
            }
        };
        let _ = resp.read_to_string(&mut response);

        let mut reader = Reader::from_str(&response);
        reader.trim_text(true);

        let mut buf = Vec::new();
        let mut found = false;
        // The `Reader` does not implement `Iterator` because it outputs borrowed data (`Cow`s)
        loop {
            match reader.read_event(&mut buf) {
                Ok(Event::Start(ref e)) => match e.name() {
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
                },
                Ok(Event::Text(e)) => {
                    if found {
                        let pool = match database::get_db() {
                            Ok(p) => p,
                            Err(_) => {
                                common::log_error(
                                    &"maintenance.rs",
                                    &"do_geocode",
                                    line!(),
                                    &"Could not get database",
                                );
                                return Err(-1);
                            }
                        };
                        let query = match database::build_query(
                            "INSERT IGNORE INTO §§.Places (picture, place) VALUES (?, ?);",
                        ) {
                            Ok(q) => q,
                            Err(_) => {
                                common::log_error(
                                    &"maintenance.rs",
                                    &"do_geocode",
                                    line!(),
                                    &"Could not build query",
                                );
                                return Err(-1);
                            }
                        };
                        let mut stmt = match pool.prepare(query) {
                            Ok(s) => s,
                            Err(e) => {
                                common::log_error(
                                    &"maintenance.rs",
                                    &"do_geocode",
                                    line!(),
                                    &format!("Could not prepare statement: {}", e),
                                );
                                continue;
                            }
                        };
                        let place = match e.unescape_and_decode(&reader) {
                            Ok(p) => p,
                            Err(e) => {
                                common::log_error(
                                    &"maintenance.rs",
                                    &"do_geocode",
                                    line!(),
                                    &format!("Could not extract place: {}", e),
                                );
                                return Err(-7);
                            }
                        };
                        match stmt.execute((id, place)) {
                            Ok(_) => {
                                //nothing
                            }
                            Err(e) => {
                                common::log_error(
                                    &"maintenance.rs",
                                    &"do_geocode",
                                    line!(),
                                    &format!("Could not execute statement: {}", e),
                                );
                                return Err(-4);
                            }
                        };
                        found = false;
                    }
                }
                Ok(Event::Eof) => break, // exits the loop when reaching end of file
                _ => (),                 // There are several other `Event`s we do not consider here
            }

            // if we don't keep a borrow elsewhere, we can clear the buffer to keep memory usage low
            buf.clear();
        }
        query = match database::build_query("UPDATE §§.Media SET doneCoding = ? WHERE id = ?;") {
            Ok(q) => q,
            Err(_) => {
                common::log_error(
                    &"maintenance.rs",
                    &"do_geocode",
                    line!(),
                    &"Could not build query",
                );
                return Err(-1);
            }
        };
        let mut stmt = match pool.prepare(query) {
            Ok(s) => s,
            Err(e) => {
                common::log_error(
                    &"maintenance.rs",
                    &"do_geocode",
                    line!(),
                    &format!("Could not prepare statement: {}", e),
                );
                continue;
            }
        };
        match stmt.execute((true, id)) {
            Ok(_) => {
                //nothing
            }
            Err(e) => {
                common::log_error(
                    &"maintenance.rs",
                    &"init",
                    line!(),
                    &format!("Could not execute statement: {}", e),
                );
                return Err(-4);
            }
        };
    }
    Ok(0)
}
fn wait_for_geocode() {
    let mut last_time = match LAST_GEOCODE.lock() {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"maintenance.rs",
                &"wait_for_geocode",
                line!(),
                &format!("Could not lock last_geocode: {}", e),
            );
            thread::sleep(stdtime::Duration::from_millis(5000));
            return;
        }
    };
    let now = common::current_time_millis();
    let wait_time: u64 = 5 * 1000;

    if *last_time + wait_time > now {
        thread::sleep(stdtime::Duration::from_millis(*last_time + wait_time - now))
    }
    *last_time = common::current_time_millis() + 2000; //assume doing geocode takes 2 secs
}

fn make_all_thumbnails() {
    let pool = match database::get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"maintenance.rs",
                &"make_all_thumbnails",
                line!(),
                &"Could not get database",
            );
            return;
        }
    };
    let query = match database::build_query("SELECT id FROM §§.Media") {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"maintenance.rs",
                &"make_all_thumbnails",
                line!(),
                &"Could not build query",
            );
            return;
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"maintenance.rs",
                &"make_all_thumbnails",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return;
        }
    };
    let result = match stmt.execute(()) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"maintenance.rs",
                &"make_all_thumbnails",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return;
        }
    };
    for wrapped_row in result {
        let row = match wrapped_row {
            Err(_) => {
                common::log_error(
                    &"maintenance.rs",
                    &"make_all_thumbnails",
                    line!(),
                    &"Error unwraping row",
                );
                continue;
            }
            Ok(row) => row,
        };
        let (id,) = match sql::from_row_opt::<(u64,)>(row) {
            Ok(e) => e,
            Err(_) => {
                continue;
            }
        };
        match make_thumbnail(id) {
            Ok(_) => {
                //nothing
            }
            Err(_) => {
                //continue;
            }
        };
    }
}
