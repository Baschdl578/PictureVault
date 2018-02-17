use image;
use image::{imageops, GenericImage};
use rexiv2;

use std::path::Path;
use std::cmp::min;
use std::fs::File;
use std::fs;
use std::process::Command;

use common;

pub struct Media {
    pub id: i64,
    pub longitude: f64,
    pub latitude: f64,
    pub path: String,
    pub filename: String,
    pub modified: u64,
    pub created: u64,
    pub h_resolution: u64,
    pub v_resolution: u64,
    pub duration: i64,
    pub size: u64,
    pub last_request: u64,
}

impl Media {
    pub fn get_thumbnail(&self, time_sensitive: bool) -> String {
        if Path::new(&self.get_thumbname(false)).exists() {
            let file = match File::open(&self.get_thumbname(false)) {
                Ok(v) => v,
                Err(_) => {
                    if self.duration < 0 {
                        self.make_picture_thumbnail(time_sensitive);
                    } else {
                        self.make_video_thumbnail(time_sensitive);
                    }
                    return String::from(self.get_thumbname(time_sensitive));
                }
            };
            let metadata = match file.metadata() {
                Ok(v) => v,
                Err(_) => {
                    if self.duration < 0 {
                        self.make_picture_thumbnail(time_sensitive);
                    } else {
                        self.make_video_thumbnail(time_sensitive);
                    }
                    return String::from(self.get_thumbname(time_sensitive));
                }
            };
            if metadata.len() > 0 {
                return String::from(self.get_thumbname(false));
            } else {
                let _ = fs::remove_file(self.get_thumbname(false));
            }
        }
        if time_sensitive {
            if Path::new(&self.get_thumbname(true)).exists() {
                return String::from(self.get_thumbname(true));
            }
        }
        if self.duration < 0 {
            self.make_picture_thumbnail(time_sensitive);
        } else {
            self.make_video_thumbnail(time_sensitive);
        }
        return String::from(self.get_thumbname(time_sensitive));
    }

    fn calc_duration(&self) -> f64 {
        if common::is_program_not_in_path("ffprobe") {
            return -1 as f64;
        }
        let output = Command::new("ffprobe")
            .arg("-v")
            .arg("error")
            .arg("-show_entries")
            .arg("format=duration")
            .arg("-of")
            .arg("default=nw=1:nk=1")
            .arg(&self.get_full_path())
            .output()
            .unwrap();
        let mut length_str = String::from(String::from_utf8_lossy(&output.stdout));

        if length_str.ends_with("\n") {
            let new_length = length_str.len() - 1;
            let _ = length_str.split_off(new_length);
        }

        let length = match length_str.parse::<f64>() {
            Ok(v) => v,
            Err(_) => {
                return -1 as f64;
            }
        };
        length
    }

    pub fn cleanup(&self) {
        if !(self.last_request + (24 * 3600 * 1000)) < common::current_time_millis() {
            return;
        }
        let mut path = String::new();
        path.push_str(&self.path);
        if !path.ends_with("/") {
            path.push('/');
        }
        path.push_str(&format!("streaming_{}/", self.id));
        let _ = fs::remove_dir_all(path);
    }

    pub fn prepare_for_streaming(&self) {
        if self.duration < 0 {
            return;
        }
        if common::is_program_not_in_path("MP4Box") {
            return;
        }
        let path = self.get_streaming_path();

        if Path::new(&path).exists() {
            return;
        }
        match fs::create_dir_all(&path) {
            Err(_) => {
                return;
            }
            _ => {}
        };

        let _ = Command::new("MP4Box")
            .arg("-dash")
            .arg("1000")
            .arg("-frag")
            .arg("1000")
            .arg("-frag-rap")
            .arg("-segment-name")
            .arg("%s_%d")
            .arg("-out")
            .arg(format!("{}{}", path, &self.filename))
            .arg("-mpd-title")
            .arg(&self.filename)
            .arg("-mpd-info-url")
            .arg(format!(
                "http://{}:{}/pulse",
                common::get_string("server_ip"),
                common::get_string("server_port")
            ))
            .arg("-base-url")
            .arg(format!(
                "http://{}:{}/media/stream/{}/",
                common::get_string("server_ip"),
                common::get_string("server_port"),
                self.id
            ))
            .arg(&self.get_full_path())
            .output()
            .unwrap();
    }

    pub fn get_streaming_path(&self) -> String {
        let mut path = String::new();
        path.push_str(&self.path);
        if !path.ends_with("/") {
            path.push('/');
        }
        path.push_str(&format!(".streaming_{}/", self.id));
        path
    }

    pub fn get_mpd_path(&self) -> String {
        self.prepare_for_streaming();
        let mut path = String::new();
        path.push_str(&format!(
            "{}{}.mpd",
            self.get_streaming_path(),
            Path::new(&self.filename)
                .file_stem()
                .unwrap()
                .to_str()
                .unwrap()
        ));
        path
    }

    fn make_video_thumbnail(&self, time_sensitive: bool) {
        if common::is_program_not_in_path("ffmpeg") {
            return;
        }
        let mut duration = self.calc_duration();
        if duration <= 0.0 {
            return;
        }

        duration = duration / 2.0;

        let tmpfile = format!("{}_tmp.jpg", self.get_full_path());

        let _ = Command::new("ffmpeg")
            .arg("-ss")
            .arg(duration.to_string())
            .arg("-i")
            .arg(self.get_full_path())
            .arg("-q:v")
            .arg("2")
            .arg("-frames:v")
            .arg("1")
            .arg(&tmpfile)
            .output();

        let img = match image::open(&Path::new(&tmpfile)) {
            Ok(v) => v,
            Err(_) => {
                let tmp = match image::open(&Path::new(&tmpfile)) {
                    //Retry once
                    Ok(v) => v,
                    Err(_) => {
                        println!("Error opening file: {}", &tmpfile);
                        return;
                    }
                };
                tmp
            }
        };
        let (width, height) = img.dimensions();
        let ratio: f32 = width as f32 / height as f32;

        let mut filter = imageops::FilterType::Lanczos3;
        let outfile = self.get_thumbname(time_sensitive);

        if time_sensitive {
            filter = imageops::FilterType::Nearest;
        }

        let _ = File::create(&outfile);

        let mut out;

        if ratio > 1.0 {
            let height = min(img.height(), 350);
            let width: f32 = height as f32 * ratio;
            out = imageops::resize(&img, width as u32, height, filter);
            let x: f32 = (out.width() as f32 - 350 as f32) / 2 as f32;
            out = imageops::crop(&mut out, x as u32, 0, 350, 350).to_image();
        } else {
            let width = min(img.width(), 350);
            let height: f32 = width as f32 / ratio;
            out = imageops::resize(&img, width, height as u32, filter);
            let y: f32 = (out.height() as f32 - 350 as f32) / 2 as f32;
            out = imageops::crop(&mut out, 0, y as u32, 350, 350).to_image();
        }

        match self.get_rotation() {
            rexiv2::Orientation::HorizontalFlip => {
                out = imageops::flip_horizontal(&out);
            }
            rexiv2::Orientation::VerticalFlip => {
                out = imageops::flip_vertical(&out);
            }
            rexiv2::Orientation::Rotate180 => {
                out = imageops::rotate180(&out);
            }
            rexiv2::Orientation::Rotate90HorizontalFlip => {
                out = imageops::rotate90(&out);
                out = imageops::flip_horizontal(&out);
            }
            rexiv2::Orientation::Rotate90 => {
                out = imageops::flip_horizontal(&out);
            }
            rexiv2::Orientation::Rotate90VerticalFlip => {
                out = imageops::rotate90(&out);
                out = imageops::flip_vertical(&out);
            }
            rexiv2::Orientation::Rotate270 => {
                out = imageops::rotate270(&out);
            }
            rexiv2::Orientation::Normal => {}
            rexiv2::Orientation::Unspecified => {}
        }

        let _ = out.save(outfile);

        if Path::new(&tmpfile).exists() {
            let _ = fs::remove_file(tmpfile);
        }

        if !time_sensitive {
            if Path::new(&self.get_thumbname(true)).exists() {
                let _ = fs::remove_file(self.get_thumbname(true));
            }
        }
    }

    fn make_picture_thumbnail(&self, time_sensitive: bool) {
        let img = match image::open(&Path::new(&self.get_full_path())) {
            Ok(v) => v,
            Err(_) => {
                let tmp = match image::open(&Path::new(&self.get_full_path())) {
                    //Retry once
                    Ok(v) => v,
                    Err(_) => {
                        println!("Error opening file: {}", &self.get_full_path());
                        return;
                    }
                };
                tmp
            }
        };
        let (width, height) = img.dimensions();
        let ratio: f32 = width as f32 / height as f32;

        let mut filter = imageops::FilterType::Lanczos3;
        let outfile = self.get_thumbname(time_sensitive);
        let _ = File::create(&outfile);

        if time_sensitive {
            filter = imageops::FilterType::Nearest;
        }

        let mut out;

        if ratio > 1.0 {
            let height = min(img.height(), 350);
            let width: f32 = height as f32 * ratio;
            out = imageops::resize(&img, width as u32, height, filter);
            let x: f32 = (out.width() as f32 - 350 as f32) / 2 as f32;
            out = imageops::crop(&mut out, x as u32, 0, 350, 350).to_image();
        } else {
            let width = min(img.width(), 350);
            let height: f32 = width as f32 / ratio;
            out = imageops::resize(&img, width, height as u32, filter);
            let y: f32 = (out.height() as f32 - 350 as f32) / 2 as f32;
            out = imageops::crop(&mut out, 0, y as u32, 350, 350).to_image();
        }

        match self.get_rotation() {
            rexiv2::Orientation::HorizontalFlip => {
                out = imageops::flip_horizontal(&out);
            }
            rexiv2::Orientation::VerticalFlip => {
                out = imageops::flip_vertical(&out);
            }
            rexiv2::Orientation::Rotate180 => {
                out = imageops::rotate180(&out);
            }
            rexiv2::Orientation::Rotate90HorizontalFlip => {
                out = imageops::rotate90(&out);
                out = imageops::flip_horizontal(&out);
            }
            rexiv2::Orientation::Rotate90 => {
                out = imageops::flip_horizontal(&out);
            }
            rexiv2::Orientation::Rotate90VerticalFlip => {
                out = imageops::rotate90(&out);
                out = imageops::flip_vertical(&out);
            }
            rexiv2::Orientation::Rotate270 => {
                out = imageops::rotate270(&out);
            }
            rexiv2::Orientation::Normal => {}
            rexiv2::Orientation::Unspecified => {}
        }

        let _ = out.save(outfile);
        if !time_sensitive {
            if Path::new(&self.get_thumbname(true)).exists() {
                let _ = fs::remove_file(self.get_thumbname(true));
            }
        }
    }

    pub fn get_full_path(&self) -> String {
        let mut fullpath: String = String::new();
        fullpath.push_str(&self.path);
        if !fullpath.ends_with("/") {
            fullpath.push_str("/")
        }
        fullpath.push_str(&self.filename);
        fullpath
    }

    fn get_rotation(&self) -> rexiv2::Orientation {
        let meta = rexiv2::Metadata::new_from_path(&self.get_full_path()).unwrap();
        if meta.supports_exif() {
            meta.get_orientation()
        } else {
            rexiv2::Orientation::Unspecified
        }
    }

    fn remove_extension_and_hide(&self) -> String {
        let fullpath = &self.get_full_path();
        let file = Path::new(fullpath).file_stem().unwrap().to_str().unwrap();
        let mut path = String::from(
            Path::new(&self.get_full_path())
                .parent()
                .unwrap()
                .to_str()
                .unwrap(),
        );
        if !path.ends_with("/") {
            path.push('/');
        }
        path.push('.');
        path.push_str(file);
        path
    }

    pub fn get_thumbname(&self, time_sensitive: bool) -> String {
        if time_sensitive {
            return format!("{}_350_quick.jpg", self.remove_extension_and_hide());
        }
        return format!("{}_350.jpg", self.remove_extension_and_hide());
    }

    pub fn new(
        id: i64,
        path: String,
        filename: String,
        created: u64,
        modified: u64,
        longitude: f64,
        latitude: f64,
        h_resolution: u64,
        v_resolution: u64,
        duration: i64,
        last_request: u64,
    ) -> Media {
        let mut fullpath: String = String::new();
        fullpath.push_str(&path);
        if !fullpath.ends_with("/") {
            fullpath.push_str("/")
        }
        fullpath.push_str(&filename);
        let size = File::open(fullpath).unwrap().metadata().unwrap().len();

        Media {
            id: id,
            longitude: latitude,
            latitude: longitude,
            path: path,
            filename: filename,
            h_resolution: h_resolution,
            v_resolution: v_resolution,
            created: created,
            modified: modified,
            duration: duration,
            size: size,
            last_request: last_request,
        }
    }

    pub fn to_string(&self) -> String {
        format!(
            "{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}\n{}",
            self.id,
            self.path,
            self.filename,
            self.created,
            self.modified,
            self.longitude,
            self.latitude,
            self.h_resolution,
            self.v_resolution,
            self.duration,
            self.size,
        )
    }
}

#[cfg(test)]
mod test {
    use super::Media;
    use std::fs;
    use std::fs::File;
    use std::io::BufReader;
    use std::io::prelude::*;
    use std::path::Path;

    use common;

    #[test]
    fn thumbnail() {
        let landscape = Media {
            id: 0,
            longitude: 0.0,
            latitude: 0.0,
            path: String::from("testdata/"),
            filename: String::from("test_landscape.jpg"),
            h_resolution: 0,
            v_resolution: 0,
            modified: 0,
            created: 0,
            duration: -1,
            size: 0,
            last_request: common::current_time_millis(),
        };

        let _ = landscape.make_picture_thumbnail(false);
        let _ = landscape.make_picture_thumbnail(true);

        let portrait = Media {
            id: 0,
            longitude: 0.0,
            latitude: 0.0,
            path: String::from("testdata"),
            filename: String::from("test_portrait.png"),
            h_resolution: 0,
            v_resolution: 0,
            modified: 0,
            created: 0,
            duration: -1,
            size: 0,
            last_request: common::current_time_millis(),
        };

        let file1: File =
            File::open(format!("{}_350_nearest.jpg", landscape.remove_extension())).unwrap();
        let mut buf_reader1 = BufReader::new(file1);
        let mut contents1 = String::new();
        let _ = buf_reader1.read_to_string(&mut contents1);

        let file2: File = File::open(landscape.get_thumbname(true)).unwrap();
        let mut buf_reader2 = BufReader::new(file2);
        let mut contents2 = String::new();
        let _ = buf_reader2.read_to_string(&mut contents2);
        assert_eq!(contents1, contents2);

        let file1: File =
            File::open(format!("{}_350_lanczos3.jpg", landscape.remove_extension())).unwrap();
        let mut buf_reader1 = BufReader::new(file1);
        let mut contents1 = String::new();
        let _ = buf_reader1.read_to_string(&mut contents1);

        let file2: File = File::open(landscape.get_thumbname(false)).unwrap();
        let mut buf_reader2 = BufReader::new(file2);
        let mut contents2 = String::new();
        let _ = buf_reader2.read_to_string(&mut contents2);
        assert_eq!(contents1, contents2);

        let _ = portrait.make_picture_thumbnail(true);

        let file1: File =
            File::open(format!("{}_350_nearest.jpg", portrait.remove_extension())).unwrap();
        let mut buf_reader1 = BufReader::new(file1);
        let mut contents1 = String::new();
        let _ = buf_reader1.read_to_string(&mut contents1);

        let file2: File = File::open(portrait.get_thumbname(true)).unwrap();
        let mut buf_reader2 = BufReader::new(file2);
        let mut contents2 = String::new();
        let _ = buf_reader2.read_to_string(&mut contents2);
        assert_eq!(contents1, contents2);

        let _ = portrait.make_picture_thumbnail(false);

        let file1: File =
            File::open(format!("{}_350_lanczos3.jpg", portrait.remove_extension())).unwrap();
        let mut buf_reader1 = BufReader::new(file1);
        let mut contents1 = String::new();
        let _ = buf_reader1.read_to_string(&mut contents1);

        let file2: File = File::open(portrait.get_thumbname(false)).unwrap();
        let mut buf_reader2 = BufReader::new(file2);
        let mut contents2 = String::new();
        let _ = buf_reader2.read_to_string(&mut contents2);
        assert_eq!(contents1, contents2);

        let _ = fs::remove_file(landscape.get_thumbname(true));
        let _ = fs::remove_file(landscape.get_thumbname(false));
        let _ = fs::remove_file(portrait.get_thumbname(false));

        assert!(!Path::new(&portrait.get_thumbname(true)).exists());

        let pic = Media {
            id: 0,
            longitude: 0.0,
            latitude: 0.0,
            path: String::from("testdata"),
            filename: String::from("video_landscape.mp4"),
            h_resolution: 5,
            v_resolution: 42,
            modified: 0,
            created: 0,
            duration: 5,
            size: 0,
            last_request: common::current_time_millis(),
        };

        let _ = pic.make_video_thumbnail(false);
        let _ = pic.make_video_thumbnail(true);

        let file1: File = File::open(format!(
            "{}_350_nearest.jpg",
            "testdata/video_landscape.mp4"
        )).unwrap();
        let mut buf_reader1 = BufReader::new(file1);
        let mut contents1 = String::new();
        let _ = buf_reader1.read_to_string(&mut contents1);

        let file2: File = File::open(pic.get_thumbname(true)).unwrap();
        let mut buf_reader2 = BufReader::new(file2);
        let mut contents2 = String::new();
        let _ = buf_reader2.read_to_string(&mut contents2);
        assert_eq!(contents1, contents2);

        let file1: File = File::open(format!(
            "{}_350_lanczos3.jpg",
            "testdata/video_landscape.mp4"
        )).unwrap();
        let mut buf_reader1 = BufReader::new(file1);
        let mut contents1 = String::new();
        let _ = buf_reader1.read_to_string(&mut contents1);

        let file2: File = File::open(pic.get_thumbname(false)).unwrap();
        let mut buf_reader2 = BufReader::new(file2);
        let mut contents2 = String::new();
        let _ = buf_reader2.read_to_string(&mut contents2);
        assert_eq!(contents1, contents2);

        let _ = fs::remove_file(pic.get_thumbname(true));
        let _ = fs::remove_file(pic.get_thumbname(false));
    }

    impl Media {
        fn remove_extension(&self) -> String {
            let fullpath = &self.get_full_path();
            let file = Path::new(fullpath).file_stem().unwrap().to_str().unwrap();
            let mut path = String::from(
                Path::new(&self.get_full_path())
                    .parent()
                    .unwrap()
                    .to_str()
                    .unwrap(),
            );
            if !path.ends_with("/") {
                path.push('/');
            }
            path.push_str(file);
            path
        }
    }
}
