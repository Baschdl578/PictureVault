use std::io::{BufRead, BufReader};
use std::fs::File;
use std::path::Path;
use std::collections::HashMap;
use std::sync::Mutex;
use std::env;
use std::fs;
use std::process::Command;

use time;

lazy_static! {
    static ref MAP : Mutex<HashMap<String, String>> = Mutex::new(HashMap::new());
}

pub fn make_hashmap_intern(testing: bool) {
    let mut hashmap = MAP.lock().unwrap();
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
                log(&"common.rs", &"get_hashmap_intern", &"Error opening file");
                return;
            }
            Ok(f) => f,
        };
        for ln in BufReader::new(file).lines() {
            let line = ln.unwrap();
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
}

fn get_string_intern(key: &str, testing: bool) -> String {
    make_hashmap_intern(testing);
    let hashmap = MAP.lock().unwrap();
    match hashmap.get(&String::from(key)) {
        Some(v) => {
            let mut out = String::new();
            out.push_str(v);
            return out;
        }
        _ => {
            return String::new();
        }
    }
}

pub fn get_string(key: &str) -> String {
    return get_string_intern(key, false);
}

pub fn get_int(key: &str) -> i32 {
    let out = get_string(key);
    match out.parse::<i32>() {
        Ok(x) => return x,
        Err(_) => {
            log(&"common.rs", &"get_string", &"Error parsing integer");
            return -1;
        }
    }
}

pub fn is_program_not_in_path(program: &str) -> bool {
    if let Ok(path) = env::var("PATH") {
        for p in path.split(":") {
            let p_str = format!("{}/{}", p, program);
            if fs::metadata(p_str).is_ok() {
                return false;
            }
        }
    }
    true
}

pub fn get_locale() -> String {
    if is_program_not_in_path("locale") {
        return String::from("en");
    }

    let output = String::from_utf8(Command::new("locale").output().unwrap().stdout).unwrap();
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
        return false;
    }
    let userstr = format!("{}:{}", user, group);

    let _ = Command::new("chown")
        .arg(userstr)
        .arg(file)
        .output()
        .unwrap();

    if is_program_not_in_path("chmod") {
        return false;
    }

    let is_dir = File::open(file).unwrap().metadata().unwrap().is_dir();
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

    let _ = Command::new("chmod")
        .arg(privileges)
        .arg(file)
        .output()
        .unwrap();

    true
}

pub fn log(file: &str, function: &str, error: &str) {
    let _ = file;
    let _ = function;
    let _ = error;
}

#[cfg(test)]
mod test {

    #[test]
    fn get_string_test() {
        assert_eq!(super::get_string_intern("test2", true), "test11");
        super::MAP.lock().unwrap().clear();
    }

}
