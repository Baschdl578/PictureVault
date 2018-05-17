use common;
use media::Media;
use mysql as sql;

use rand::{thread_rng, Rng};
use std::collections::LinkedList;
use std::process::exit;
use std::sync::Mutex;

lazy_static! {
    static ref DB: Mutex<sql::Pool> = {
        let pool = match make_db() {
            Ok(p) => p,
            Err(_) => {
                common::log_error(
                    &"database.rs",
                    &"lazy_static",
                    line!(),
                    &"Could not make new database",
                );
                exit(3);
            }
        };
        Mutex::new(pool)
    };
}

pub fn get_db() -> Result<sql::Pool, i8> {
    return match DB.lock() {
        Ok(db) => Ok(db.clone()),
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_db",
                line!(),
                &"Could not lock database",
            );
            Err(-1)
        }
    };
}

fn make_db() -> Result<sql::Pool, i8> {
    let db_user: String = match common::get_string("db_user") {
        Ok(s) => s,
        Err(_) => {
            common::log_error(&"database", &"make_db", line!(), "Could not get db user");
            return Err(-5);
        }
    };
    let db_pass: String = match common::get_string("db_pass") {
        Ok(s) => s,
        Err(_) => {
            common::log_error(&"database", &"make_db", line!(), "Could not get db pass");
            return Err(-5);
        }
    };
    let db_addr: String = match common::get_string("db_address") {
        Ok(s) => s,
        Err(_) => {
            common::log_error(&"database", &"make_db", line!(), "Could not get db address");
            return Err(-5);
        }
    };
    let db_port: i32 = match common::get_int("db_port") {
        Ok(s) => s,
        Err(_) => {
            common::log_error(&"database", &"make_db", line!(), "Could not get db port");
            return Err(-5);
        }
    };

    let connection: String = format!("mysql://{}:{}@{}:{}", db_user, db_pass, db_addr, db_port);

    return match sql::Pool::new(connection) {
        Ok(e) => Ok(e),
        Err(e) => {
            common::log_error(
                &"database.rs",
                "make_db",
                line!(),
                &format!("Error making database: {}", e),
            );
            Err(-1)
        }
    };
}

pub fn get_db_name() -> Result<String, i8> {
    return common::get_string("db_name");
}

pub fn build_query(query: &str) -> Result<String, i8> {
    let name = match get_db_name() {
        Ok(s) => s,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"match build_query",
                line!(),
                "Could not get database name",
            );
            return Err(-1);
        }
    };
    return Ok(str::replace(query, "§§", &name).to_string());
}

fn join_lists(
    list: &mut LinkedList<(u64, String, u64, (u64, u64, u64, u64))>,
    list2: &LinkedList<(u64, String, u64, (u64, u64, u64, u64))>,
    name: String,
) {
    for &(id, ref name2, count, (sa1, sa2, sa3, sa4)) in list2.iter() {
        if name.eq(name2) {
            list.push_front((id, name2.to_string(), count, (sa1, sa2, sa3, sa4)));
        }
    }
}

pub fn init() -> Result<i8, i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(&"database.rs", &"init", line!(), &"Could not get database");
            return Err(-1);
        }
    };
    let mut query = match build_query("CREATE DATABASE IF NOT EXISTS §§;") {
        Ok(q) => q,
        Err(_) => {
            common::log_error(&"database.rs", &"init", line!(), &"Could not build query");
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"init",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    match stmt.execute(()) {
        Ok(_) => {
            //nothing
        }
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"init",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-3);
        }
    };
    query = match build_query(
        "CREATE TABLE IF NOT EXISTS §§.Library (id INT NOT NULL AUTO_INCREMENT,
        name TEXT NOT NULL, PRIMARY KEY (id))
        ENGINE=INNODB CHARACTER SET utf8 COLLATE utf8_unicode_ci;",
    ) {
        Ok(q) => q,
        Err(_) => {
            common::log_error(&"database.rs", &"init", line!(), &"Could not build query");
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"init",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    match stmt.execute(()) {
        Ok(_) => {
            //nothing
        }
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"init",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-3);
        }
    };

    query = match build_query(
        "CREATE TABLE IF NOT EXISTS §§.Users
        (id INT NOT NULL AUTO_INCREMENT, path TEXT NOT NULL, lastsync BIGINT,
        email TEXT NOT NULL, pass TEXT NOT NULL, os_user TEXT NOT NULL,
        os_group TEXT NOT NULL, group_visible TINYINT NOT NULL DEFAULT '0',
        PRIMARY KEY (id)) ENGINE=INNODB CHARACTER SET utf8 COLLATE utf8_unicode_ci;",
    ) {
        Ok(q) => q,
        Err(_) => {
            common::log_error(&"database.rs", &"init", line!(), &"Could not build query");
            return Err(-3);
        }
    };
    stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"init",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    match stmt.execute(()) {
        Ok(_) => {
            //nothing
        }
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"init",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-3);
        }
    };

    query = match build_query(
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
    ) {
        Ok(q) => q,
        Err(_) => {
            common::log_error(&"database.rs", &"init", line!(), &"Could not build query");
            return Err(-3);
        }
    };
    stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"init",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    match stmt.execute(()) {
        Ok(_) => {
            //nothing
        }
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"init",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-3);
        }
    };

    query = match build_query(
        "CREATE TABLE IF NOT EXISTS §§.Places (id INT NOT NULL AUTO_INCREMENT,
        picture INT NOT NULL, place VARCHAR(255) NOT NULL, PRIMARY KEY (id),
        UNIQUE (picture, place), FOREIGN KEY (picture) REFERENCES Media(id))
        ENGINE=INNODB CHARACTER SET utf8 COLLATE utf8_unicode_ci;",
    ) {
        Ok(q) => q,
        Err(_) => {
            common::log_error(&"database.rs", &"init", line!(), &"Could not build query");
            return Err(-3);
        }
    };
    stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"init",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    match stmt.execute(()) {
        Ok(_) => {
            //nothing
        }
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"init",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-3);
        }
    };
    Ok(0)
}

pub fn get_user_id_and_verify(user: &str, hash: &str) -> Result<u64, i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_user_id_and_verify",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };

    let query = match build_query(&"SELECT id, pass FROM §§.Users WHERE email = ?;") {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_user_id_and_verify",
                line!(),
                &"Could not build query",
            );
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_user_id_and_verify",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    let result = match stmt.execute((&user,)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_user_id_and_verify",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };

    for row in result {
        let naked_row = match row {
            Err(_) => {
                common::log_error(
                    &"database.rs",
                    &"get_user_id_and_verify",
                    line!(),
                    &"Error unwraping row",
                );
                continue;
            }
            Ok(row) => row,
        };
        let (id, pass) = match sql::from_row_opt::<(u64, String)>(naked_row) {
            Ok(v) => v,
            Err(_) => {
                continue;
            }
        };
        if str::eq(&pass, hash) {
            return Ok(id);
        }
    }
    return Err(-5);
}

pub fn get_libs(user: u64) -> Result<LinkedList<(u64, String, u64, (u64, u64, u64, u64))>, i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_libs",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query(
        "SELECT id, name FROM §§.Library WHERE id IN
        (SELECT DISTINCT library FROM §§.Media WHERE user = ?);",
    ) {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_libs",
                line!(),
                &"Could not build query",
            );
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_libs",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    let result = match stmt.execute((user,)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_libs",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };

    let mut list: LinkedList<(u64, String, u64, (u64, u64, u64, u64))> = LinkedList::new();
    let mut list2: LinkedList<(u64, String, u64, (u64, u64, u64, u64))> = LinkedList::new();

    for row in result {
        let naked_row = match row {
            Err(_) => {
                common::log_error(&"database.rs", &"get_libs", line!(), &"Error unwraping row");
                continue;
            }
            Ok(row) => row,
        };
        let (id, name) = match sql::from_row_opt::<(u64, String)>(naked_row) {
            Ok(v) => v,
            Err(_) => {
                continue;
            }
        };
        let samples = match get_samples(id, user) {
            Ok(s) => s,
            Err(_) => {
                common::log_error(
                    &"database.rs",
                    &"get_libs",
                    line!(),
                    &"Could not get samples",
                );
                return Err(-5);
            }
        };

        let count = match get_media_count(id, user) {
            Ok(s) => s,
            Err(_) => {
                common::log_error(
                    &"database.rs",
                    &"get_libs",
                    line!(),
                    &"Could not get media count",
                );
                return Err(-5);
            }
        };

        if name == "Camera" || name == "Kamera" || name == "WhatsApp Images" || name == "Instagram"
            || name == "Google Fotos" || name == "OneDrive" || name == "Messenger"
            || name == "PhotoDirector"
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

    return Ok(list);
}

fn get_media_count(id: u64, user: u64) -> Result<u64, i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_media_count",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query(
        "SELECT COUNT(id) AS `total` FROM (SELECT id FROM §§.Media WHERE library = ? AND user = ?) AS `other` ",
    ) {
            Ok(q)   => q,
            Err(_)  => {
                common::log_error(&"database.rs", &"get_media_count", line!(), &"Could not build query");
                return Err(-3);
            }
        };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_media_count",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    let result = match stmt.execute((id, user)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_media_count",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };

    for row in result {
        let naked_row = match row {
            Err(_) => {
                common::log_error(&"database.rs", &"get_libs", line!(), &"Error unwraping row");
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
        return Ok(count);
    }
    return Err(-5);
}

pub fn ownership_info(user: u64) -> Result<(String, String, bool), i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"ownership_info",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query =
        match build_query("SELECT os_user, os_group, group_visible FROM §§.Users WHERE id = ?") {
            Ok(q) => q,
            Err(_) => {
                common::log_error(
                    &"database.rs",
                    &"ownership_info",
                    line!(),
                    &"Could not build query",
                );
                return Err(-3);
            }
        };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"ownership_info",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    let result = match stmt.execute((user,)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"ownership_info",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };

    for row in result {
        let naked_row = match row {
            Err(_) => {
                common::log_error(
                    &"database.rs",
                    &"get_pics_by_lib",
                    line!(),
                    &"Error unwraping row",
                );
                continue;
            }
            Ok(row) => row,
        };
        let (user, group, visible) = match sql::from_row_opt::<(String, String, i8)>(naked_row) {
            Ok(v) => v,
            Err(_) => {
                continue;
            }
        };

        return Ok((user, group, visible == 1));
    }
    return Err(-5);
}

fn get_samples(libid: u64, user: u64) -> Result<(u64, u64, u64, u64), i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_samples",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query("SELECT id FROM §§.Media WHERE user = ? AND library = ?") {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_samples",
                line!(),
                &"Could not build query",
            );
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_samples",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    let result = match stmt.execute((user, libid)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_samples",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };

    let mut list: Vec<u64> = Vec::new();
    for row in result {
        let naked_row = match row {
            Err(_) => {
                common::log_error(
                    &"database.rs",
                    &"get_samples",
                    line!(),
                    &"Error unwraping row",
                );
                continue;
            }
            Ok(row) => row,
        };
        let id = match sql::from_row_opt::<u64>(naked_row) {
            Ok(v) => v,
            Err(_) => {
                continue;
            }
        };

        list.push(id);
    }

    let count = list.len();
    if count == 0 {
        return Err(-5);
    }
    if count == 1 {
        let id = list[0];
        return Ok((id, 0, 0, 0));
    }
    if count == 2 {
        let id = list[0];
        let id2 = list[1];
        return Ok((id, id2, 0, 0));
    }
    if count == 3 {
        let id = list[0];
        let id2 = list[1];
        let id3 = list[2];
        return Ok((id, id2, id3, 0));
    }
    if count == 4 {
        let id = list[0];
        let id2 = list[1];
        let id3 = list[2];
        let id4 = list[3];
        return Ok((id, id2, id3, id4));
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
        return Ok((id, id2, id3, id4));
    }
    if count == 6 {
        let mut rng = thread_rng();
        let offset = rng.gen_range(0, 3);
        let id = list[0 + offset];
        let id2 = list[1 + offset];
        let id3 = list[2 + offset];
        let id4 = list[3 + offset];
        return Ok((id, id2, id3, id4));
    }
    if count == 7 {
        let mut rng = thread_rng();
        let offset = rng.gen_range(0, 4);
        let id = list[0 + offset];
        let id2 = list[1 + offset];
        let id3 = list[2 + offset];
        let id4 = list[3 + offset];
        return Ok((id, id2, id3, id4));
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
    return Ok((id1, id2, id3, id4));
}

pub fn get_pics_by_lib(libid: u64, user: u64) -> Result<LinkedList<(u64, String, i64, u64)>, i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_pics_by_lib",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query(
        "SELECT id, filename, duration, size FROM §§.Media WHERE user = ? AND library = ? ORDER BY
        (CASE WHEN created < modified THEN created ELSE modified END) DESC,
        filename DESC",
    ) {
            Ok(q)   => q,
            Err(_)  => {
                common::log_error(&"database.rs", &"get_pics_by_lib", line!(), &"Could not build query");
                return Err(-3);
            }
        };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_pics_by_lib",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    let result = match stmt.execute((user, libid)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_pics_by_lib",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };

    let mut list: LinkedList<(u64, String, i64, u64)> = LinkedList::new();

    for row in result {
        let naked_row = match row {
            Err(_) => {
                common::log_error(
                    &"database.rs",
                    &"get_pics_by_lib",
                    line!(),
                    &"Error unwraping row",
                );
                continue;
            }
            Ok(row) => row,
        };
        let (id, name, duration, size) =
            match sql::from_row_opt::<(u64, String, i64, u64)>(naked_row) {
                Ok(v) => v,
                Err(_) => {
                    continue;
                }
            };

        list.push_back((id, name, duration, size));
    }
    return Ok(list);
}

pub fn get_userpath(uid: u64) -> Result<String, i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_userpath",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query("SELECT path FROM §§.Users WHERE id = ?;") {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_userpath",
                line!(),
                &"Could not build query",
            );
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_userpath",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    let result = match stmt.execute((uid,)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_userpath",
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
                    &"database.rs",
                    &"get_userpath",
                    line!(),
                    &"Error unwraping row",
                );
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
        return Ok(out);
    }
    return Err(-5);
}

fn add_library(bucket: &String) -> Result<u8, i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"add_library",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query("INSERT IGNORE INTO §§.Library (name) VALUES (?);") {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"add_library",
                line!(),
                &"Could not build query",
            );
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"add_library",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    match stmt.execute((bucket,)) {
        Ok(_) => {
            //nothing
        }
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"add_library",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };
    return Ok(0);
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
    size: u64,
    uid: u64,
) -> Result<u64, i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"add_media",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query(
        "INSERT IGNORE INTO §§.Media (path, filename, created, modified,
        latitude, longitude, horiz_resolution, vert_resolution, library, duration, user, size) VALUES
        (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);",
    ) {
            Ok(q)   => q,
            Err(_)  => {
                common::log_error(&"database.rs", &"add_media", line!(), &"Could not build query");
                return Err(-3);
            }
        };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"add_media",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    let bucket_copy = format!("{}", &bucket);
    let lib_id = match get_lib_id_from_name(bucket) {
        Ok(i) => i,
        Err(_) => {
            match add_library(&bucket_copy) {
                Ok(_) => {
                    //nothing
                }
                Err(_) => {
                    common::log_error(
                        &"database.rs",
                        &"add_media",
                        line!(),
                        &"Could not add library",
                    );
                    return Err(-5);
                }
            }
            match get_lib_id_from_name(bucket_copy) {
                Ok(i) => i,
                Err(_) => {
                    common::log_error(
                        &"database.rs",
                        &"add_media",
                        line!(),
                        &"Could not get library id",
                    );
                    return Err(-5);
                }
            }
        }
    };
    let tmp_path = format!("{}", &path);
    let tmp_filename = format!("{}", &filename);

    match stmt.execute((
        tmp_path,
        tmp_filename,
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
    )) {
        Ok(_) => {
            //nothing
        }
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"add_media",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    }

    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"add_media",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query("SELECT id FROM §§.Media WHERE path = ? AND filename = ?;") {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"add_media",
                line!(),
                &"Could not build query",
            );
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"add_media",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };

    let result = match stmt.execute((path, filename)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"add_media",
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
                    &"database.rs",
                    &"add_media",
                    line!(),
                    &"Error unwraping row",
                );
                continue;
            }
            Ok(row) => row,
        };
        let id: u64 = match sql::from_row_opt::<u64>(row) {
            Ok(v) => v,
            _ => {
                continue;
            }
        };
        return Ok(id);
    }
    return Err(-5);
}

fn get_lib_id_from_name(lib_name: String) -> Result<u64, i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_lib_id_from_name",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query("SELECT id FROM §§.Library WHERE name = ?;") {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_lib_id_from_name",
                line!(),
                &"Could not build query",
            );
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_lib_id_from_name",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    let result = match stmt.execute((lib_name,)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_lib_id_from_name",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };

    for wrapped_row in result {
        let row = match wrapped_row {
            Err(_) => {
                common::log_error(&"database.rs", &"get", line!(), &"Error unwraping row");
                continue;
            }
            Ok(row) => row,
        };
        let id: u64 = match sql::from_row_opt::<u64>(row) {
            Ok(v) => v,
            _ => {
                continue;
            }
        };
        return Ok(id);
    }
    return Err(-5);
}

pub fn get_mediainfo(user: u64, mediaid: u64) -> Result<Media, i16> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_mediainfo",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query =
        match build_query("UPDATE §§.Media SET last_request = ? WHERE id = ? AND user = ?;") {
            Ok(q) => q,
            Err(_) => {
                common::log_error(
                    &"database.rs",
                    &"get_mediainfo",
                    line!(),
                    &"Could not build query",
                );
                return Err(-3);
            }
        };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_mediainfo",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    match stmt.execute((common::current_time_millis(), mediaid, user)) {
        Ok(_) => {
            //nothing
        }
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_mediainfo",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };

    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_mediainfo",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query(
        "SELECT id, path, filename, longitude, latitude, created, modified,
        horiz_resolution, vert_resolution, duration, last_request FROM §§.Media
        WHERE user = ? AND id = ?",
    ) {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_mediainfo",
                line!(),
                &"Could not build query",
            );
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_mediainfo",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    let result = match stmt.execute((user, mediaid)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_mediainfo",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };

    for wrapped_row in result {
        let mut row = match wrapped_row {
            Err(_) => {
                common::log_error(
                    &"database.rs",
                    &"get_media",
                    line!(),
                    &"Error unwraping row",
                );
                continue;
            }
            Ok(row) => row,
        };

        let id: u64 = match row.take_opt(0) {
            Some(v) => match v {
                Ok(v) => v,
                Err(e) => {
                    common::log_error(
                        &"database.rs",
                        &"get_mediainfo",
                        line!(),
                        &format!("Could not take from row: {}", e),
                    );
                    continue;
                }
            },
            None => {
                common::log_error(
                    &"database.rs",
                    &"get_mediainfo",
                    line!(),
                    &"Could not take from row",
                );
                continue;
            }
        };
        let path: String = match row.take_opt(1) {
            Some(v) => match v {
                Ok(v) => v,
                Err(e) => {
                    common::log_error(
                        &"database.rs",
                        &"get_mediainfo",
                        line!(),
                        &format!("Could not take from row: {}", e),
                    );
                    continue;
                }
            },
            None => {
                common::log_error(
                    &"database.rs",
                    &"get_mediainfo",
                    line!(),
                    &"Could not take from row",
                );
                continue;
            }
        };
        let filename: String = match row.take_opt(2) {
            Some(v) => match v {
                Ok(v) => v,
                Err(e) => {
                    common::log_error(
                        &"database.rs",
                        &"get_mediainfo",
                        line!(),
                        &format!("Could not take from row: {}", e),
                    );
                    continue;
                }
            },
            None => {
                common::log_error(
                    &"database.rs",
                    &"get_mediainfo",
                    line!(),
                    &"Could not take from row",
                );
                continue;
            }
        };
        let longitude: f64 = match row.take_opt(3) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0.0,
            },
            None => 0.0,
        };
        let latitude: f64 = match row.take_opt(4) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0.0,
            },
            None => 0.0,
        };
        let created: u64 = match row.take_opt(5) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0,
            },
            None => 0,
        };
        let modified: u64 = match row.take_opt(6) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0,
            },
            None => 0,
        };
        let h_res: u64 = match row.take_opt(7) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0,
            },
            None => 0,
        };
        let v_res: u64 = match row.take_opt(8) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0,
            },
            None => 0,
        };
        let duration: i64 = match row.take_opt(9) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => -1,
            },
            None => -1,
        };
        let last_request: u64 = match row.take_opt(10) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0,
            },
            None => 0,
        };
        return match Media::new(
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
        ) {
            Ok(m) => Ok(m),
            Err(_) => Err(-2),
        };
    }
    return Err(1);
}

pub fn remove_by_id(mediaid: u64) -> Result<u8, i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"remove_by_id",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query(
        "DELETE FROM §§.Places
        WHERE picture = ?",
    ) {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"remove_by_id",
                line!(),
                &"Could not build query",
            );
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"remove_by_id",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    match stmt.execute((mediaid,)) {
        Ok(_) => {
            //nothing
        }
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"remove_by_id",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };

    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"remove_by_id",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };

    let query = match build_query(
        "DELETE FROM §§.Media
        WHERE id = ?",
    ) {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"remove_by_id",
                line!(),
                &"Could not build query",
            );
            return Err(-3);
        }
    };

    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"remove_by_id",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    match stmt.execute((mediaid,)) {
        Ok(_) => {
            //nothing
        }
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"remove_by_id",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };
    Ok(0)
}

pub fn get_mediainfo_by_id(mediaid: u64) -> Result<Media, i16> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_mediainfo_by_id",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query(
        "SELECT id, path, filename, longitude, latitude, created, modified,
        horiz_resolution, vert_resolution, duration, last_request FROM §§.Media
        WHERE id = ?",
    ) {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_mediainfo_by_id",
                line!(),
                &"Could not build query",
            );
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_mediainfo_by_id",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    let result = match stmt.execute((mediaid,)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_mediainfo_by_id",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };

    for wrapped_row in result {
        let mut row = match wrapped_row {
            Err(_) => {
                common::log_error(
                    &"database.rs",
                    &"get_media_by_id",
                    line!(),
                    &"Error unwraping row",
                );
                continue;
            }
            Ok(row) => row,
        };

        let id: u64 = match row.take_opt(0) {
            Some(v) => match v {
                Ok(v) => v,
                Err(e) => {
                    common::log_error(
                        &"database.rs",
                        &"get_mediainfo_by_id",
                        line!(),
                        &format!("Could not take from row: {}", e),
                    );
                    continue;
                }
            },
            None => {
                common::log_error(
                    &"database.rs",
                    &"get_mediainfo_by_id",
                    line!(),
                    &"Could not take from row",
                );
                continue;
            }
        };
        let path: String = match row.take_opt(1) {
            Some(v) => match v {
                Ok(v) => v,
                Err(e) => {
                    common::log_error(
                        &"database.rs",
                        &"get_mediainfo_by_id",
                        line!(),
                        &format!("Could not take from row: {}", e),
                    );
                    continue;
                }
            },
            None => {
                common::log_error(
                    &"database.rs",
                    &"get_mediainfo_by_id",
                    line!(),
                    &"Could not take from row",
                );
                continue;
            }
        };
        let filename: String = match row.take_opt(2) {
            Some(v) => match v {
                Ok(v) => v,
                Err(e) => {
                    common::log_error(
                        &"database.rs",
                        &"get_mediainfo_by_id",
                        line!(),
                        &format!("Could not take from row: {}", e),
                    );
                    continue;
                }
            },
            None => {
                common::log_error(
                    &"database.rs",
                    &"get_mediainfo_by_id",
                    line!(),
                    &"Could not take from row",
                );
                continue;
            }
        };
        let longitude: f64 = match row.take_opt(3) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0.0,
            },
            None => 0.0,
        };
        let latitude: f64 = match row.take_opt(4) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0.0,
            },
            None => 0.0,
        };
        let created: u64 = match row.take_opt(5) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0,
            },
            None => 0,
        };
        let modified: u64 = match row.take_opt(6) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0,
            },
            None => 0,
        };
        let h_res: u64 = match row.take_opt(7) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0,
            },
            None => 0,
        };
        let v_res: u64 = match row.take_opt(8) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0,
            },
            None => 0,
        };
        let duration: i64 = match row.take_opt(9) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => -1,
            },
            None => -1,
        };
        let last_request: u64 = match row.take_opt(10) {
            Some(v) => match v {
                Ok(v) => v,
                Err(_) => 0,
            },
            None => 0,
        };
        match Media::new(
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
        ) {
            Ok(m) => {
                return Ok(m);
            }
            Err(_) => {
                continue;
            }
        };
    }
    return Err(1);
}

pub fn get_lastsync(uid: u64) -> Result<u64, i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_lastsync",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query("SELECT lastsync FROM §§.Users WHERE id = ?") {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"get_lastsync",
                line!(),
                &"Could not build query",
            );
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_lastsync",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    let result = match stmt.execute((uid,)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"get_lastsync",
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
                    &"database.rs",
                    &"get_userpath",
                    line!(),
                    &"Error unwraping row",
                );
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
        return Ok(value);
    }
    return Err(-5);
}

pub fn set_lastsync(uid: u64, lastsync: u64) -> Result<u8, i8> {
    let pool = match get_db() {
        Ok(db) => db,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"set_lastsync",
                line!(),
                &"Could not get database",
            );
            return Err(-1);
        }
    };
    let query = match build_query("UPDATE §§.Users SET lastsync = ? WHERE id = ?") {
        Ok(q) => q,
        Err(_) => {
            common::log_error(
                &"database.rs",
                &"set_lastsync",
                line!(),
                &"Could not build query",
            );
            return Err(-3);
        }
    };
    let mut stmt = match pool.prepare(query) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"set_lastsync",
                line!(),
                &format!("Could not prepare statement: {}", e),
            );
            return Err(-2);
        }
    };
    match stmt.execute((lastsync, uid)) {
        Ok(s) => s,
        Err(e) => {
            common::log_error(
                &"database.rs",
                &"set_lastsync",
                line!(),
                &format!("Could not execute statement: {}", e),
            );
            return Err(-4);
        }
    };
    return Ok(0);
}
