package models.library;

public class Library {
    private int id;
    private String name;
    private String address;
    private String phone;
    private double latitude;
    private double longitude;

    public Library() {}

    public Library(int id, String name, String address, String phone, double latitude, double longitude) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters & Setters
    public int getId()                      { return id; }
    public void setId(int id)               { this.id = id; }
    public String getName()                 { return name; }
    public void setName(String name)        { this.name = name; }
    public String getAddress()              { return address; }
    public void setAddress(String address)  { this.address = address; }
    public String getPhone()                { return phone; }
    public void setPhone(String phone)      { this.phone = phone; }
    public double getLatitude()             { return latitude; }
    public void setLatitude(double lat)     { this.latitude = lat; }
    public double getLongitude()            { return longitude; }
    public void setLongitude(double lon)    { this.longitude = lon; }

    @Override
    public String toString() { return name; }
}
