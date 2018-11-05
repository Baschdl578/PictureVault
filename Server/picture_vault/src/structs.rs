use actix_web::{fs::NamedFile, Responder, HttpResponse, HttpRequest};
use std::io::{Error, ErrorKind};

#[derive(Deserialize)]
pub struct Id {
    pub id: u64,
}

#[derive(Deserialize)]
pub struct MediaInfo {
    pub bucket: String,
    pub filename: String,
    pub latitude: f64,
    pub longitude: f64,
    pub created: u64,
    pub modified: u64,
    pub duration: i64,
    pub h_res: u64,
    pub v_res: u64,
    pub size: u64,
}

#[derive(Deserialize)]
pub struct LastSync {
    pub time: u64,
}


pub struct MyResponse {
    file: Option<NamedFile>,
    response: Option<HttpResponse>,
}

impl MyResponse {
    pub fn new_file(file: NamedFile) -> MyResponse {
        MyResponse {
            file: Some(file),
            response: None
        }
    }

    pub fn new_response(response: HttpResponse) -> MyResponse {
        
        MyResponse {
            file: None,
            response: Some(response),
        }
    }
}

impl Responder for MyResponse {
    type Item = HttpResponse;
    type Error = std::io::Error;

    fn respond_to<S: 'static>(self, req: &HttpRequest<S>) -> Result<HttpResponse, std::io::Error> {
        let file = match self.file {
            Some(f) => f,
            None    => { 
                match self.response {
                    Some(r)   => {return Ok(r)},
                    None      => {return Err(Error::from(ErrorKind::NotFound));}
                }
            }
        };
        return file.respond_to(req);
    }
}
