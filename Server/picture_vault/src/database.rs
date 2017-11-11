use mysql as sql;
use common;
use media::Media;

use std::collections::LinkedList;
use std::fs::File;
use rand::{thread_rng, Rng};
use std::sync::Mutex;

lazy_static! {
    static ref DB : Mutex<sql::Pool> = Mutex::new(make_db());
}

pub fn get_db() -> sql::Pool {
    return DB.lock().unwrap().clone();
}

fn make_db() -> sql::Pool {
    let db_user: String = common::get_string("db_user");
    let db_pass: String = common::get_string("db_pass");
    let db_addr: String = common::get_string("db_address");
    let db_port: i32 = common::get_int("db_port");

    let connection: String = String::from(format!(
        "mysql://{}:{}@{}:{}",
        db_user,
        db_pass,
        db_addr,
        db_port
    ));

    return sql::Pool::new(connection).unwrap();
}

pub fn get_db_name() -> String {
    return common::get_string("db_name");
}

pub fn build_query(query: &str) -> String {
    return str::replace(query, "§§", &get_db_name()).to_string();
}

fn join_lists(
    list: &mut LinkedList<(i64, String, u64, (i64, i64, i64, i64))>,
    list2: &LinkedList<(i64, String, u64, (i64, i64, i64, i64))>,
    name: String,
) {
    for &(id, ref name2, count, (sa1, sa2, sa3, sa4)) in list2.iter() {
        if name.eq(name2) {
            list.push_front((id, name2.to_string(), count, (sa1, sa2, sa3, sa4)));
        }
    }
}

pub fn init() {
    let pool = get_db();

    let mut stmt = pool.prepare(build_query("CREATE DATABASE IF NOT EXISTS §§;"))
        .unwrap();
    let _ = stmt.execute(()).unwrap();


    let mut stmt = pool.prepare(build_query(
        "CREATE TABLE IF NOT EXISTS §§.Library (id INT NOT NULL AUTO_INCREMENT,
        name TEXT NOT NULL, PRIMARY KEY (id))
        ENGINE=INNODB CHARACTER SET utf8 COLLATE utf8_unicode_ci;",
    )).unwrap();
    let _ = stmt.execute(()).unwrap();

    stmt = pool.prepare(build_query(
        "CREATE TABLE IF NOT EXISTS §§.Users
        (id INT NOT NULL AUTO_INCREMENT, path TEXT NOT NULL, lastsync BIGINT,
        email TEXT NOT NULL, pass TEXT NOT NULL, PRIMARY KEY (id))
        ENGINE=INNODB CHARACTER SET utf8 COLLATE utf8_unicode_ci;",
    )).unwrap();
    let _ = stmt.execute(()).unwrap();

    stmt = pool.prepare(build_query(
        "CREATE TABLE IF NOT EXISTS §§.Media
        (id INT NOT NULL AUTO_INCREMENT, path VARCHAR(255) NOT NULL,
        filename VARCHAR(255) NOT NULL, longitude DOUBLE, latitude DOUBLE,
        created BIGINT, modified BIGINT NOT NULL, horiz_resolution INT NOT NULL,
        vert_resolution INT NOT NULL, duration BIGINT NOT NULL,
        library INT NOT NULL, user INT NOT NULL, doneCoding BOOLEAN NOT NULL,
        last_request BIGINT, size BIGINT,
        PRIMARY KEY (id), FOREIGN KEY (library) REFERENCES Library(id),
        FOREIGN KEY (user) REFERENCES Users(id), UNIQUE (path, filename))
        ENGINE=INNODB CHARACTER SET utf8 COLLATE utf8_unicode_ci;",
    )).unwrap();
    let _ = stmt.execute(()).unwrap();

    stmt = pool.prepare(build_query(
        "CREATE TABLE IF NOT EXISTS §§.Places (id INT NOT NULL AUTO_INCREMENT,
        picture INT NOT NULL, place VARCHAR(255) NOT NULL, PRIMARY KEY (id),
        UNIQUE (picture, place), FOREIGN KEY (picture) REFERENCES Media(id))
        ENGINE=INNODB CHARACTER SET utf8 COLLATE utf8_unicode_ci;",
    )).unwrap();
    let _ = stmt.execute(()).unwrap();
}

pub fn get_user_id_and_verify(user: &str, hash: &str) -> i64 {
    let pool = get_db();

    let mut stmt = pool.prepare(build_query(
        &"SELECT id, pass FROM §§.Users WHERE email = ?;",
    )).unwrap();
    let result = stmt.execute((&user,)).unwrap();

    for row in result {
        let naked_row = match row {
            Err(_) => {
                common::log(
                    &"database.rs",
                    &"get_user_id_and_verify",
                    &"Error unwraping row",
                );
                continue;
            }
            Ok(row) => row,
        };
        let (id, pass) = match sql::from_row_opt::<(i64, String)>(naked_row) {
            Ok(v) => v,
            Err(_) => {
                continue;
            }
        };
        if str::eq(&pass, hash) {
            return id;
        }
    }
    return -1;
}

pub fn get_libs(user: i64) -> LinkedList<(i64, String, u64, (i64, i64, i64, i64))> {
    let pool = get_db();
    let query = build_query(
        "SELECT id, name FROM §§.Library WHERE id IN
        (SELECT DISTINCT library FROM §§.Media WHERE user = ?);",
    );
    let mut stmt = pool.prepare(query).unwrap();
    let result = stmt.execute((user,)).unwrap();

    let mut list: LinkedList<(i64, String, u64, (i64, i64, i64, i64))> = LinkedList::new();
    let mut list2: LinkedList<(i64, String, u64, (i64, i64, i64, i64))> = LinkedList::new();


    for row in result {
        let naked_row = match row {
            Err(_) => {
                common::log(&"database.rs", &"get_libs", &"Error unwraping row");
                continue;
            }
            Ok(row) => row,
        };
        let (id, name) = match sql::from_row_opt::<(i64, String)>(naked_row) {
            Ok(v) => v,
            Err(_) => {
                continue;
            }
        };
        let samples = get_samples(id, user);

        let count = get_media_count(id, user);

        if name == "Camera" || name == "Kamera" || name == "WhatsApp Images" ||
            name == "Instagram" || name == "Google Fotos" || name == "OneDrive" ||
            name == "Messenger" || name == "PhotoDirector"
        {
            list2.push_back((id, name, count, samples));
        } else {
            list.push_back((id, name, count, samples));
        }
    }
    join_lists(&mut list, &list2, "PhotoDirector".to_string());
    join_lists(&mut list, &list2, "Instagram".to_string());
    join_lists(&mut list, &list2, "Messenger".to_string());
    join_lists(&mut list, &list2, "OneDrive".to_string());
    join_lists(&mut list, &list2, "Google Fotos".to_string());
    join_lists(&mut list, &list2, "WhatsApp Images".to_string());
    join_lists(&mut list, &list2, "Camera".to_string());
    join_lists(&mut list, &list2, "Kamera".to_string());

    return list;
}

fn get_media_count(id: i64, user: i64) -> u64 {
    let pool = get_db();
    let query = build_query(
        "SELECT COUNT(id) AS `total` FROM (SELECT id FROM §§.Media WHERE library = ? AND user = ?) AS `other` ",
    );
    let mut stmt = pool.prepare(query).unwrap();
    let result = stmt.execute((id, user)).unwrap();

    for row in result {
        let naked_row = match row {
            Err(_) => {
                common::log(&"database.rs", &"get_libs", &"Error unwraping row");
                continue;
            }
            Ok(row) => row,
        };
        let count = match sql::from_row_opt::<u64>(naked_row) {
            Ok(v) => v,
            Err(_) => {
                continue;
            }
        };
        return count;
    }
    return 0;
}

fn get_samples(libid: i64, user: i64) -> (i64, i64, i64, i64) {
    let pool = get_db();
    let query = build_query("SELECT id FROM §§.Media WHERE user = ? AND library = ?");
    let mut stmt = pool.prepare(query).unwrap();
    let result = stmt.execute((user, libid));

    let list: Vec<i64> = result
        .map(|result| {
            // In this closure we will map `QueryResult` to `Vec<Payment>`
            // `QueryResult` is iterator over `MyResult<row, err>` so first call to `map`
            // will map each `MyResult` to contained `row` (no proper error handling)
            // and second call to `map` will map each `row` to `Payment`
            result
                .map(|x| x.unwrap())
                .map(|row| sql::from_row::<i64>(row))
                .collect() // Collect payments so now `QueryResult` is mapped to `Vec<Payment>`
        })
        .unwrap(); // Unwrap `Vec<Payment>`

    let count = list.len();
    if count == 0 {
        return (-1, -1, -1, -1);
    }
    if count == 1 {
        let id = list[0];
        return (id, -1, -1, -1);
    }
    if count == 2 {
        let id = list[0];
        let id2 = list[1];
        return (id, id2, -1, -1);
    }
    if count == 3 {
        let id = list[0];
        let id2 = list[1];
        let id3 = list[2];
        return (id, id2, id3, -1);
    }
    if count == 4 {
        let id = list[0];
        let id2 = list[1];
        let id3 = list[2];
        let id4 = list[3];
        return (id, id2, id3, id4);
    }
    if count == 5 {
        let id = list[0];
        let id2 = list[1];
        let id3 = list[2];
        let id4;
        if common::current_time_millis() % 2 == 0 {
            id4 = list[3];
        } else {
            id4 = list[4];
        }
        return (id, id2, id3, id4);
    }
    if count == 6 {
        let mut rng = thread_rng();
        let offset = rng.gen_range(0, 3);
        let id = list[0 + offset];
        let id2 = list[1 + offset];
        let id3 = list[2 + offset];
        let id4 = list[3 + offset];
        return (id, id2, id3, id4);
    }
    if count == 7 {
        let mut rng = thread_rng();
        let offset = rng.gen_range(0, 4);
        let id = list[0 + offset];
        let id2 = list[1 + offset];
        let id3 = list[2 + offset];
        let id4 = list[3 + offset];
        return (id, id2, id3, id4);
    }


    let mut rng = thread_rng();
    let num1: usize = rng.gen_range(0, (count / 4) - 1);
    let num2: usize = rng.gen_range(count / 4, (count / 2) - 1);
    let num3: usize = rng.gen_range(count / 2, (3 * count / 4) - 1);
    let num4: usize = rng.gen_range(3 * count / 4, count - 1);


    let id1 = list[num1];
    let id2 = list[num2];
    let id3 = list[num3];
    let id4 = list[num4];
    return (id1, id2, id3, id4);
}

pub fn get_pics_by_lib(libid: i64, user: i64) -> LinkedList<(i64, String, i64, u64)> {
    let pool = get_db();
    let query = build_query(
        "SELECT id, filename, duration, size FROM §§.Media WHERE user = ? AND library = ? ORDER BY
        (CASE WHEN created < modified THEN created ELSE modified END) DESC,
        filename DESC",
    );
    let mut stmt = pool.prepare(query).unwrap();
    let result = stmt.execute((user, libid)).unwrap();

    let mut list: LinkedList<(i64, String, i64, u64)> = LinkedList::new();

    for row in result {
        let naked_row = match row {
            Err(_) => {
                common::log(&"database.rs", &"get_pics_by_lib", &"Error unwraping row");
                continue;
            }
            Ok(row) => row,
        };
        let (id, name, duration, size) =
            match sql::from_row_opt::<(i64, String, i64, u64)>(naked_row) {
                Ok(v) => v,
                Err(_) => {
                    continue;
                }
            };

        list.push_back((id, name, duration, size));
    }
    return list;
}

pub fn get_userpath(uid: i64) -> String {
    let pool = get_db();
    let query = build_query("SELECT path FROM §§.Users WHERE id = ?;");
    let mut stmt = pool.prepare(query).unwrap();
    let result = stmt.execute((uid,)).unwrap();

    for wrapped_row in result {
        let row = match wrapped_row {
            Err(_) => {
                common::log(&"database.rs", &"get_userpath", &"Error unwraping row");
                continue;
            }
            Ok(row) => row,
        };
        let out: String = match sql::from_row_opt::<String>(row) {
            Ok(v) => v,
            _ => {
                continue;
            }
        };
        return out;

    }
    return String::new();
}

fn add_library(bucket: &String) {
    let pool = get_db();
    let query = build_query("INSERT IGNORE INTO §§.Library (name) VALUES (?);");
    let mut stmt = pool.prepare(query).unwrap();
    let _ = stmt.execute((bucket,)).unwrap();

}

pub fn add_media(
    path: String,
    filename: String,
    bucket: String,
    created: u64,
    modified: u64,
    latitude: f64,
    longitude: f64,
    h_res: u64,
    v_res: u64,
    duration: i64,
    uid: i64,
) -> i64 {
    let pool = get_db();
    let query = build_query(
        "INSERT IGNORE INTO §§.Media (path, filename, created, modified,
        latitude, longitude, horiz_resolution, vert_resolution, library, duration, user, size) VALUES
        (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);",
    );
    let mut stmt = pool.prepare(query).unwrap();
    let bucket_copy = format!("{}", &bucket);
    let lib_id = get_lib_id_from_name(bucket);
    if lib_id <= 0 {
        add_library(&bucket_copy);
    }
    let lib_id = get_lib_id_from_name(bucket_copy);
    if lib_id <= 0 {
        return -1;
    }
    let tmp_path = format!("{}", &path);
    let tmp_file = format!("{}", &filename);

    let mut fullpath: String = String::new();
    fullpath.push_str(&path);
    if !fullpath.ends_with("/") {
        fullpath.push_str("/")
    }
    fullpath.push_str(&filename);
    let size = File::open(fullpath).unwrap().metadata().unwrap().len();

    let _ = stmt.execute((
        tmp_path,
        tmp_file,
        created,
        modified,
        latitude,
        longitude,
        h_res,
        v_res,
        lib_id,
        duration,
        uid,
        size,
    )).unwrap();


    let pool = get_db();
    let query = build_query("SELECT id FROM §§.Media WHERE path = ? AND filename = ?;");
    let mut stmt = pool.prepare(query).unwrap();

    let result = stmt.execute((path, filename)).unwrap();
    for wrapped_row in result {
        let row = match wrapped_row {
            Err(_) => {
                common::log(&"database.rs", &"add_media", &"Error unwraping row");
                continue;
            }
            Ok(row) => row,
        };
        let id: i64 = match sql::from_row_opt::<i64>(row) {
            Ok(v) => v,
            _ => {
                continue;
            }
        };
        return id;

    }
    return -1;

}

fn get_lib_id_from_name(lib_name: String) -> i64 {
    let pool = get_db();
    let query = build_query("SELECT id FROM §§.Library WHERE name = ?;");
    let mut stmt = pool.prepare(query).unwrap();
    let result = stmt.execute((lib_name,)).unwrap();

    for wrapped_row in result {
        let row = match wrapped_row {
            Err(_) => {
                common::log(&"database.rs", &"get", &"Error unwraping row");
                continue;
            }
            Ok(row) => row,
        };
        let id: i64 = match sql::from_row_opt::<i64>(row) {
            Ok(v) => v,
            _ => {
                continue;
            }
        };
        return id;

    }
    return -1;

}


pub fn get_mediainfo(user: i64, mediaid: i64) -> Result<Media, i16> {
    let pool = get_db();
    let query = build_query(
        "UPDATE §§.Media SET last_request = ? WHERE id = ? AND user = ?;",
    );
    let mut stmt = pool.prepare(query).unwrap();
    let _ = stmt.execute((common::current_time_millis(), mediaid, user))
        .unwrap();


    let pool = get_db();
    let query = build_query(
        "SELECT id, path, filename, longitude, latitude, created, modified,
        horiz_resolution, vert_resolution, duration, last_request FROM §§.Media
        WHERE user = ? AND id = ?",
    );
    let mut stmt = pool.prepare(query).unwrap();
    let result = stmt.execute((user, mediaid)).unwrap();

    for wrapped_row in result {
        let mut row = match wrapped_row {
            Err(_) => {
                common::log(&"database.rs", &"get_media", &"Error unwraping row");
                continue;
            }
            Ok(row) => row,
        };

        let id: i64 = match row.take_opt(0).unwrap() {
            Ok(v) => v,
            _ => {
                continue;
            }
        };
        let path: String = match row.take_opt(1).unwrap() {
            Ok(v) => v,
            _ => {
                continue;
            }
        };
        let filename: String = match row.take_opt(2).unwrap() {
            Ok(v) => v,
            _ => {
                continue;
            }
        };
        let longitude: f64 = match row.take_opt(3).unwrap() {
            Ok(v) => v,
            _ => 0.0,
        };
        let latitude: f64 = match row.take_opt(4).unwrap() {
            Ok(v) => v,
            _ => 0.0,
        };
        let created: u64 = match row.take_opt(5).unwrap() {
            Ok(v) => v,
            _ => 0,
        };
        let modified: u64 = match row.take_opt(6).unwrap() {
            Ok(v) => v,
            _ => 0,
        };
        let h_res: u64 = match row.take_opt(7).unwrap() {
            Ok(v) => v,
            _ => 0,
        };
        let v_res: u64 = match row.take_opt(8).unwrap() {
            Ok(v) => v,
            _ => 0,
        };
        let duration: i64 = match row.take_opt(9).unwrap() {
            Ok(v) => v,
            _ => -1,
        };
        let last_request: u64 = match row.take_opt(10).unwrap() {
            Ok(v) => v,
            _ => 0,
        };
        return Ok(Media::new(
            id,
            path,
            filename,
            created,
            modified,
            longitude,
            latitude,
            h_res,
            v_res,
            duration,
            last_request,
        ));
    }
    return Err(1);
}

pub fn get_mediainfo_by_id(mediaid: i64) -> Result<Media, i16> {
    let pool = get_db();
    let query = build_query(
        "SELECT id, path, filename, longitude, latitude, created, modified,
        horiz_resolution, vert_resolution, duration, last_request FROM §§.Media
        WHERE id = ?",
    );
    let mut stmt = pool.prepare(query).unwrap();
    let result = stmt.execute((mediaid,)).unwrap();

    for wrapped_row in result {
        let mut row = match wrapped_row {
            Err(_) => {
                common::log(&"database.rs", &"get_media_by_id", &"Error unwraping row");
                continue;
            }
            Ok(row) => row,
        };

        let id: i64 = match row.take_opt(0).unwrap() {
            Ok(v) => v,
            _ => {
                continue;
            }
        };
        let path: String = match row.take_opt(1).unwrap() {
            Ok(v) => v,
            _ => {
                continue;
            }
        };
        let filename: String = match row.take_opt(2).unwrap() {
            Ok(v) => v,
            _ => {
                continue;
            }
        };
        let longitude: f64 = match row.take_opt(3).unwrap() {
            Ok(v) => v,
            _ => 0.0,
        };
        let latitude: f64 = match row.take_opt(4).unwrap() {
            Ok(v) => v,
            _ => 0.0,
        };
        let created: u64 = match row.take_opt(5).unwrap() {
            Ok(v) => v,
            _ => 0,
        };
        let modified: u64 = match row.take_opt(6).unwrap() {
            Ok(v) => v,
            _ => 0,
        };
        let h_res: u64 = match row.take_opt(7).unwrap() {
            Ok(v) => v,
            _ => 0,
        };
        let v_res: u64 = match row.take_opt(8).unwrap() {
            Ok(v) => v,
            _ => 0,
        };
        let duration: i64 = match row.take_opt(9).unwrap() {
            Ok(v) => v,
            _ => -1,
        };
        let last_request: u64 = match row.take_opt(10).unwrap() {
            Ok(v) => v,
            _ => 0,
        };
        return Ok(Media::new(
            id,
            path,
            filename,
            created,
            modified,
            longitude,
            latitude,
            h_res,
            v_res,
            duration,
            last_request,
        ));
    }
    return Err(1);
}

pub fn get_lastsync(uid: i64) -> u64 {
    let pool = get_db();
    let query = build_query("SELECT lastsync FROM §§.Users WHERE id = ?");
    let mut stmt = pool.prepare(query).unwrap();
    let result = stmt.execute((uid,)).unwrap();

    for wrapped_row in result {
        let row = match wrapped_row {
            Err(_) => {
                common::log(&"database.rs", &"get_userpath", &"Error unwraping row");
                continue;
            }
            Ok(row) => row,
        };
        let value: u64 = match sql::from_row_opt::<u64>(row) {
            Ok(v) => v,
            _ => {
                continue;
            }
        };
        return value;
    }
    return 0;
}

pub fn set_lastsync(uid: i64, lastsync: u64) {
    let pool = get_db();
    let query = build_query("UPDATE §§.Users SET lastsync = ? WHERE id = ?");
    let mut stmt = pool.prepare(query).unwrap();
    let _ = stmt.execute((lastsync, uid)).unwrap();
}
