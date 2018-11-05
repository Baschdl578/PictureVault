package de.smschindler.picturevault.model;

/**
 * Created by baschdl on 22.08.17.
 */

public class Media {
    private Long id;
    private String path;
    private String filename;
    private Double latitude;
    private Double longitude;
    private Long created;
    private Long modified;
    private Long h_res;
    private Long v_res;
    private Long duration;
    private Long size;

    public Media(Long id, String path, String filename, Double latitude, Double longitude, Long created, Long modified, Long h_res, Long v_res, Long duration, Long size) {
        this.id = id;
        this.path = path;
        this.filename = filename;
        this.latitude = latitude;
        this.longitude = longitude;
        this.created = created;
        this.modified = modified;
        this.h_res = h_res;
        this.v_res = v_res;
        this.duration = duration;
        this.size = size;
    }

    public boolean isVideo() {
        return duration >= 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public Long getModified() {
        return modified;
    }

    public void setModified(Long modified) {
        this.modified = modified;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public Long getH_res() {
        return h_res;
    }

    public void setH_res(Long h_res) {
        this.h_res = h_res;
    }

    public Long getV_res() {
        return v_res;
    }

    public void setV_res(Long v_res) {
        this.v_res = v_res;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getResolution() {
        return getH_res() + "x" + getV_res();
    }
}
