package com.nadia.plugin.worldmap.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data model representing an official aircraft registration record retrieved
 * from the
 * Federal Aviation Administration (FAA) Civil Aviation Registry.
 *
 * <p>
 * Stores comprehensive aircraft certification, airframe specifications,
 * powerplant details,
 * Mode S transponder identifiers, registration status, and registered owner
 * information parsed
 * from the FAA N-Number inquiry portal
 * ({@code https://registry.faa.gov/aircraftinquiry/Search/NNumberInquiry}).
 *
 * <p>
 * <b>Lifecycle &amp; Thread Safety:</b>
 * Instances of this record are safe for concurrent reads across background
 * flight tracking schedulers,
 * JavaFX WebView bridge callbacks, and in-memory caches once populated.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FaaAircraftRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default FAA N-Number inquiry portal URL. */
    public static final String DEFAULT_INQUIRY_URL = "https://registry.faa.gov/aircraftinquiry/Search/NNumberInquiry";

    @JsonProperty("nNumber")
    private String nNumber;

    @JsonProperty("serialNumber")
    private String serialNumber;

    @JsonProperty("status")
    private String status;

    @JsonProperty("manufacturer")
    private String manufacturer;

    @JsonProperty("model")
    private String model;

    @JsonProperty("typeAircraft")
    private String typeAircraft;

    @JsonProperty("typeEngine")
    private String typeEngine;

    @JsonProperty("mfrYear")
    private String mfrYear;

    @JsonProperty("modeSCodeHex")
    private String modeSCodeHex;

    @JsonProperty("modeSCodeOct")
    private String modeSCodeOct;

    @JsonProperty("certificateIssueDate")
    private String certificateIssueDate;

    @JsonProperty("expirationDate")
    private String expirationDate;

    @JsonProperty("typeRegistration")
    private String typeRegistration;

    @JsonProperty("ownerName")
    private String ownerName;

    @JsonProperty("ownerStreet")
    private String ownerStreet;

    @JsonProperty("ownerCity")
    private String ownerCity;

    @JsonProperty("ownerState")
    private String ownerState;

    @JsonProperty("ownerZip")
    private String ownerZip;

    @JsonProperty("ownerCountry")
    private String ownerCountry;

    @JsonProperty("airworthinessClassification")
    private String airworthinessClassification;

    @JsonProperty("engineModel")
    private String engineModel;

    @JsonProperty("recordFound")
    private boolean recordFound;

    @JsonProperty("inquiryUrl")
    private String inquiryUrl = DEFAULT_INQUIRY_URL;

    @JsonProperty("errorMessage")
    private String errorMessage;

    /**
     * Default no-argument constructor for deserialization and builder population.
     */
    public FaaAircraftRecord() {
        this.recordFound = false;
        this.inquiryUrl = DEFAULT_INQUIRY_URL;
    }

    /**
     * Creates a minimal or not-found record with the specified N-number and status
     * flag.
     *
     * @param nNumber     the tail registration number
     * @param recordFound whether an active or reserved registry record exists
     */
    public FaaAircraftRecord(String nNumber, boolean recordFound) {
        this.nNumber = nNumber;
        this.recordFound = recordFound;
        this.inquiryUrl = DEFAULT_INQUIRY_URL;
    }

    /**
     * Creates an error or fallback record with an explanatory error message.
     *
     * @param nNumber      the tail registration number
     * @param errorMessage human-readable error or explanation
     * @return populated error record
     */
    public static FaaAircraftRecord createErrorRecord(String nNumber, String errorMessage) {
        FaaAircraftRecord record = new FaaAircraftRecord(nNumber, false);
        record.setErrorMessage(errorMessage);
        return record;
    }

    /**
     * Retrieves the FAA registration N-number.
     *
     * @return the N-number (e.g. {@code "N900AA"})
     */
    public String getNNumber() {
        return nNumber;
    }

    /**
     * Sets the FAA registration N-number.
     *
     * @param nNumber registration tail number
     */
    public void setNNumber(String nNumber) {
        this.nNumber = nNumber;
    }

    /**
     * Retrieves the manufacturer serial number.
     *
     * @return airframe serial number
     */
    public String getSerialNumber() {
        return serialNumber;
    }

    /**
     * Sets the manufacturer serial number.
     *
     * @param serialNumber airframe serial number
     */
    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    /**
     * Retrieves the FAA certificate status (e.g. {@code "Valid"},
     * {@code "Reserved"}).
     *
     * @return certification status string
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the FAA certificate status.
     *
     * @param status certificate status
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Retrieves the airframe manufacturer name.
     *
     * @return aircraft manufacturer (e.g. {@code "BOEING"}, {@code "CESSNA"})
     */
    public String getManufacturer() {
        return manufacturer;
    }

    /**
     * Sets the airframe manufacturer name.
     *
     * @param manufacturer aircraft manufacturer
     */
    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    /**
     * Retrieves the aircraft model designation.
     *
     * @return model identifier string
     */
    public String getModel() {
        return model;
    }

    /**
     * Sets the aircraft model designation.
     *
     * @param model model identifier
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Retrieves the aircraft classification type.
     *
     * @return aircraft type (e.g. {@code "Fixed Wing Single-Engine"})
     */
    public String getTypeAircraft() {
        return typeAircraft;
    }

    /**
     * Sets the aircraft classification type.
     *
     * @param typeAircraft aircraft type
     */
    public void setTypeAircraft(String typeAircraft) {
        this.typeAircraft = typeAircraft;
    }

    /**
     * Retrieves the engine classification type.
     *
     * @return powerplant type (e.g. {@code "4 Cycle"}, {@code "Turbo-Fan"})
     */
    public String getTypeEngine() {
        return typeEngine;
    }

    /**
     * Sets the engine classification type.
     *
     * @param typeEngine engine type
     */
    public void setTypeEngine(String typeEngine) {
        this.typeEngine = typeEngine;
    }

    /**
     * Retrieves the year of manufacture.
     *
     * @return 4-digit manufacture year string
     */
    public String getMfrYear() {
        return mfrYear;
    }

    /**
     * Sets the year of manufacture.
     *
     * @param mfrYear manufacture year
     */
    public void setMfrYear(String mfrYear) {
        this.mfrYear = mfrYear;
    }

    /**
     * Retrieves the Mode S transponder address in hexadecimal (Base 16).
     *
     * @return 6-character hex string (e.g. {@code "AC6F2D"})
     */
    public String getModeSCodeHex() {
        return modeSCodeHex;
    }

    /**
     * Sets the Mode S transponder address in hexadecimal.
     *
     * @param modeSCodeHex 6-character hex string
     */
    public void setModeSCodeHex(String modeSCodeHex) {
        this.modeSCodeHex = modeSCodeHex;
    }

    /**
     * Retrieves the Mode S transponder address in octal (Base 8).
     *
     * @return octal code string
     */
    public String getModeSCodeOct() {
        return modeSCodeOct;
    }

    /**
     * Sets the Mode S transponder address in octal.
     *
     * @param modeSCodeOct octal code string
     */
    public void setModeSCodeOct(String modeSCodeOct) {
        this.modeSCodeOct = modeSCodeOct;
    }

    /**
     * Retrieves the certificate issue date.
     *
     * @return issue date string (MM/DD/YYYY)
     */
    public String getCertificateIssueDate() {
        return certificateIssueDate;
    }

    /**
     * Sets the certificate issue date.
     *
     * @param certificateIssueDate issue date string
     */
    public void setCertificateIssueDate(String certificateIssueDate) {
        this.certificateIssueDate = certificateIssueDate;
    }

    /**
     * Retrieves the registration certificate expiration date.
     *
     * @return expiration date string (MM/DD/YYYY)
     */
    public String getExpirationDate() {
        return expirationDate;
    }

    /**
     * Sets the registration certificate expiration date.
     *
     * @param expirationDate expiration date string
     */
    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    /**
     * Retrieves the registrant entity classification type (e.g. {@code "LLC"},
     * {@code "Corporation"}, {@code "Individual"}).
     *
     * @return registration type
     */
    public String getTypeRegistration() {
        return typeRegistration;
    }

    /**
     * Sets the registrant entity classification type.
     *
     * @param typeRegistration registration type
     */
    public void setTypeRegistration(String typeRegistration) {
        this.typeRegistration = typeRegistration;
    }

    /**
     * Retrieves the registered owner or operating organization name.
     *
     * @return owner name
     */
    public String getOwnerName() {
        return ownerName;
    }

    /**
     * Sets the registered owner name.
     *
     * @param ownerName owner name
     */
    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    /**
     * Retrieves the registered owner street address.
     *
     * @return street address
     */
    public String getOwnerStreet() {
        return ownerStreet;
    }

    /**
     * Sets the registered owner street address.
     *
     * @param ownerStreet street address
     */
    public void setOwnerStreet(String ownerStreet) {
        this.ownerStreet = ownerStreet;
    }

    /**
     * Retrieves the registered owner city.
     *
     * @return city name
     */
    public String getOwnerCity() {
        return ownerCity;
    }

    /**
     * Sets the registered owner city.
     *
     * @param ownerCity city name
     */
    public void setOwnerCity(String ownerCity) {
        this.ownerCity = ownerCity;
    }

    /**
     * Retrieves the registered owner state or province.
     *
     * @return state name
     */
    public String getOwnerState() {
        return ownerState;
    }

    /**
     * Sets the registered owner state.
     *
     * @param ownerState state name
     */
    public void setOwnerState(String ownerState) {
        this.ownerState = ownerState;
    }

    /**
     * Retrieves the registered owner postal / zip code.
     *
     * @return zip code
     */
    public String getOwnerZip() {
        return ownerZip;
    }

    /**
     * Sets the registered owner postal / zip code.
     *
     * @param ownerZip zip code
     */
    public void setOwnerZip(String ownerZip) {
        this.ownerZip = ownerZip;
    }

    /**
     * Retrieves the registered owner country.
     *
     * @return country name
     */
    public String getOwnerCountry() {
        return ownerCountry;
    }

    /**
     * Sets the registered owner country.
     *
     * @param ownerCountry country name
     */
    public void setOwnerCountry(String ownerCountry) {
        this.ownerCountry = ownerCountry;
    }

    /**
     * Retrieves the FAA airworthiness classification.
     *
     * @return airworthiness classification
     */
    public String getAirworthinessClassification() {
        return airworthinessClassification;
    }

    /**
     * Sets the FAA airworthiness classification.
     *
     * @param airworthinessClassification classification string
     */
    public void setAirworthinessClassification(String airworthinessClassification) {
        this.airworthinessClassification = airworthinessClassification;
    }

    /**
     * Retrieves the engine model designation.
     *
     * @return engine model
     */
    public String getEngineModel() {
        return engineModel;
    }

    /**
     * Sets the engine model designation.
     *
     * @param engineModel engine model
     */
    public void setEngineModel(String engineModel) {
        this.engineModel = engineModel;
    }

    /**
     * Indicates whether an official FAA registration record was successfully
     * located.
     *
     * @return {@code true} if valid record exists in FAA database
     */
    public boolean isRecordFound() {
        return recordFound;
    }

    /**
     * Sets whether an official FAA registration record was found.
     *
     * @param recordFound record found flag
     */
    public void setRecordFound(boolean recordFound) {
        this.recordFound = recordFound;
    }

    /**
     * Retrieves the official inquiry portal URL for this inquiry.
     *
     * @return FAA inquiry portal URL
     */
    public String getInquiryUrl() {
        return inquiryUrl;
    }

    /**
     * Sets the official inquiry portal URL.
     *
     * @param inquiryUrl inquiry portal URL
     */
    public void setInquiryUrl(String inquiryUrl) {
        this.inquiryUrl = inquiryUrl;
    }

    /**
     * Retrieves error message if the inquiry failed or was unresolvable.
     *
     * @return error message or {@code null}
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message.
     *
     * @param errorMessage error explanation
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Converts this record into a structured key-value map suitable for JSON
     * serialization and map overlays.
     *
     * @return map of populated aircraft fields
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("nNumber", nNumber);
        map.put("serialNumber", serialNumber);
        map.put("status", status);
        map.put("manufacturer", manufacturer);
        map.put("model", model);
        map.put("typeAircraft", typeAircraft);
        map.put("typeEngine", typeEngine);
        map.put("mfrYear", mfrYear);
        map.put("modeSCodeHex", modeSCodeHex);
        map.put("modeSCodeOct", modeSCodeOct);
        map.put("certificateIssueDate", certificateIssueDate);
        map.put("expirationDate", expirationDate);
        map.put("typeRegistration", typeRegistration);
        map.put("ownerName", ownerName);
        map.put("ownerStreet", ownerStreet);
        map.put("ownerCity", ownerCity);
        map.put("ownerState", ownerState);
        map.put("ownerZip", ownerZip);
        map.put("ownerCountry", ownerCountry);
        map.put("airworthinessClassification", airworthinessClassification);
        map.put("engineModel", engineModel);
        map.put("recordFound", recordFound);
        map.put("inquiryUrl", inquiryUrl);
        map.put("errorMessage", errorMessage);
        return map;
    }
}
