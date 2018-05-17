use std::collections::HashMap;
use std::env;
use std::fs;
use std::fs::File;
use std::io::Read;
use std::io::{BufRead, BufReader};
use std::path::Path;
use std::process::Command;
use std::process::exit;
use std::sync::Mutex;

use time;

lazy_static! {
    static ref MAP: Mutex<HashMap<String, String>> = Mutex::new(HashMap::new());
}

pub fn make_hashmap_intern(testing: bool) -> Result<i8, i8> {
    let mut hashmap = match MAP.lock() {
        Ok(m) => m,
        Err(_) => {
            log_error(
                &"common.rs",
                &"make_hashmap_intern",
                line!(),
                &"Could not lock hashMap",
            );
            return Err(-1);
        }
    };
    if testing {
        hashmap.clear();
    }
    if hashmap.is_empty() {
        let filename = match testing {
            true => "testdata/config",
            false => "/etc/picture_vault.conf",
        };
        let path = Path::new(filename);
        let file = match File::open(&path) {
            Err(_) => {
                log_error(
                    &"common.rs",
                    &"make_hashmap_intern",
                    line!(),
                    &"Error opening file",
                );
                return Err(-2);
            }
            Ok(f) => f,
        };
        for ln in BufReader::new(file).lines() {
            let line = match ln {
                Ok(l) => l,
                Err(e) => {
                    log_info(
                        &"common.rs",
                        &"make_hashmap_intern",
                        line!(),
                        &format!("Could not read line: {}", e),
                    );
                    continue;
                }
            };
            let split: Vec<String> = line.split("=").map(|s| s.to_string()).collect();
            let key_tmp = match split.get(0) {
                None => {
                    continue;
                }
                Some(s) => s,
            };
            let value_tmp = match split.get(1) {
                None => {
                    continue;
                }
                Some(s) => s,
            };
            let mut key = String::new();
            let mut val = String::new();
            key.push_str(&key_tmp);
            val.push_str(&value_tmp);

            hashmap.insert(key, val);
        }
    }
    return Ok(0);
}

fn get_string_intern(key: &str, testing: bool) -> Result<String, i8> {
    match make_hashmap_intern(testing) {
        Ok(_) => {
            //nothing
        }
        Err(_) => {
            exit(4);
        }
    }
    let hashmap = match MAP.lock() {
        Ok(h) => h,
        Err(e) => {
            log_error(
                &"common.rs",
                &"get_string_intern",
                line!(),
                &format!("Could not lock HashMap: {}", e),
            );
            return Err(-1);
        }
    };
    match hashmap.get(&String::from(key)) {
        Some(v) => {
            let mut out = String::new();
            out.push_str(v);
            return Ok(out);
        }
        _ => {
            return Err(-2);
        }
    }
}

pub fn get_string(key: &str) -> Result<String, i8> {
    return get_string_intern(key, false);
}

pub fn get_int(key: &str) -> Result<i32, i8> {
    let out = match get_string(key) {
        Ok(s) => s,
        Err(e) => {
            return Err(e);
        }
    };
    match out.parse::<i32>() {
        Ok(x) => return Ok(x),
        Err(_) => {
            log_error(&"common.rs", &"get_int", line!(), &"Error parsing integer");
            return Err(-3);
        }
    }
}

pub fn is_program_not_in_path(program: &str) -> bool {
    if let Ok(path) = env::var("PATH") {
        for p in path.split(":") {
            let p_str = format!("{}/{}", p, program);
            if fs::metadata(p_str).is_ok() {
                return false;
            } /*
            if p.ends_with(format!("/{}", program)) {
                if fs::metadata(p).is_ok() {
                    return false;
                }
            } */
        }
    }
    log_error(
        &"common.rs",
        &"is_program_not_in_path",
        line!(),
        &format!("Could not find program: {}", program),
    );
    true
}

pub fn get_locale() -> String {
    if is_program_not_in_path("locale") {
        return String::from("en");
    }

    let result = match Command::new("locale").output() {
        Ok(r) => r,
        Err(_) => {
            return String::from("en");
        }
    };
    let output = match String::from_utf8(result.stdout) {
        Ok(o) => o,
        Err(_) => {
            return String::from("en");
        }
    };
    for l in output.lines() {
        let line = String::from(l.trim());
        if line.starts_with("LANG=") {
            let (_, out) = line.split_at(5);
            let ret = out.trim().trim_matches('\n').trim_matches('\"');
            return String::from(ret);
        }
    }
    return String::from("en");
}

pub fn current_time_millis() -> u64 {
    let time = time::get_time();
    let now: u64 = (time.sec as u64 * 1000) + (time.nsec as u64 / 1000 / 1000);
    now
}

pub fn change_owner(file: &str, user: &str, group: &str, visible: bool) -> bool {
    if is_program_not_in_path("chown") {
        println!("Could not find chown");
        return false;
    }
    let userstr = format!("{}:{}", user, group);

    match Command::new("chown").arg(userstr).arg(file).output() {
        Ok(_) => {
            //nothing
        }
        Err(_) => {
            return false;
        }
    }

    if is_program_not_in_path("chmod") {
        println!("Could not find chmod");
        return false;
    }
    let ffile = match File::open(file) {
        Ok(f) => f,
        Err(_) => {
            log_error(&"common.rs", "change_owner", line!(), "Could not open file");
            return false;
        }
    };
    let metadata = match ffile.metadata() {
        Ok(f) => f,
        Err(_) => {
            log_error(
                &"common.rs",
                "change_owner",
                line!(),
                "Could not get file metadata",
            );
            return false;
        }
    };
    let is_dir = metadata.is_dir();
    let privileges;
    if is_dir {
        if visible {
            privileges = "770"
        } else {
            privileges = "700"
        }
    } else {
        if visible {
            privileges = "660"
        } else {
            privileges = "600"
        }
    }

    match Command::new("chmod").arg(privileges).arg(file).output() {
        Ok(_) => {
            //nothing
        }
        Err(_) => {
            return false;
        }
    };

    true
}

#[allow(dead_code)]
pub fn load_file(file: &str) -> Result<Vec<u8>, u8> {
    let mut f = match File::open(file) {
        Ok(f) => f,
        Err(_) => {
            return Err(1);
        }
    };

    let mut buffer = vec![0; 10];
    match f.read_to_end(&mut buffer) {
        Err(_) => {
            return Err(2);
        }
        Ok(_) => {
            //nothing
        }
    };
    return Ok(buffer);
}

pub fn log_info(file: &str, function: &str, line: u32, error: &str) {
    info!(
        "Info from {}, function {} at line {} with message {}",
        file, function, line, error
    );
}

pub fn log_error(file: &str, function: &str, line: u32, error: &str) {
    error!(
        "Error in {}, function {} at line {} with message {}",
        file, function, line, error
    );
}

#[allow(dead_code)]
pub fn log_debug(file: &str, function: &str, line: u32, error: &str) {
    debug!(
        "Debug message from {}, function {} at line {} with message {}",
        file, function, line, error
    );
}

#[cfg(test)]
mod test {

    #[test]
    fn get_string_test() {
        assert_eq!(super::get_string_intern("test2", true).unwrap(), "test11");
        super::MAP.lock().unwrap().clear();
    }

}
