package com.nadia.plugin.worldmap.service;

import com.nadia.plugin.worldmap.model.FaaAircraftRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FaaRegistryServiceTest {

    private FaaRegistryService service;

    @BeforeEach
    public void setUp() {
        service = new FaaRegistryService(5);
    }

    @Test
    public void testParseFaaResultHtmlActiveAircraft() {
        String htmlSample = """
                <div id="mainDiv">
                    <h1>FAA REGISTRY</h1>
                    <h3><b>N-Number Inquiry Results</b></h3>
                    <div class="devkit-simple-table-wrapper">
                        <table class="devkit-table">
                            <caption class="devkit-table-title">N-Number Entered: 900aa</caption>
                            <caption class="devkit-table-title">Aircraft Description</caption>
                            <tbody>
                                <tr class="devkit-table-row">
                                    <td data-label="">Serial Number</td>
                                    <td data-label="Serial Number">2024 2246</td>
                                    <td data-label="">Status</td>
                                    <td data-label="Status">Valid</td>
                                </tr>
                                <tr class="devkit-table-row">
                                    <td data-label="">Manufacturer Name</td>
                                    <td data-label="Manufacturer Name">EVEKTOR-AEROTECHNIK A S</td>
                                    <td data-label="">Certificate Issue Date</td>
                                    <td data-label="Certificate Issue Date">12/17/2024</td>
                                </tr>
                                <tr class="devkit-table-row">
                                    <td data-label="">Model</td>
                                    <td data-label="Model">HARMONY LSA</td>
                                    <td data-label="">Expiration Date</td>
                                    <td data-label="Expiration Date">12/31/2031</td>
                                </tr>
                                <tr class="devkit-table-row">
                                    <td data-label="">Type Aircraft</td>
                                    <td data-label="Aircraft Type">Fixed Wing Single-Engine</td>
                                    <td data-label="">Type Engine</td>
                                    <td data-label="Engine Type">4 Cycle</td>
                                </tr>
                                <tr class="devkit-table-row">
                                    <td data-label="">MFR Year</td>
                                    <td data-label="Mfr Year">2024</td>
                                    <td data-label="">Mode S Code (Base 16 / Hex)</td>
                                    <td data-label="Mode S Code (Base 16 / Hex)">AC6F2D</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                    <div class="devkit-simple-table-wrapper">
                        <table class="devkit-table">
                            <caption class="devkit-table-title">Registered Owner</caption>
                            <tbody>
                                <tr class="devkit-table-row">
                                    <td data-label="" colspan="1">Name</td>
                                    <td data-label="Name" colspan="3">ERDMAN AVIATION LLC</td>
                                </tr>
                                <tr class="devkit-table-row">
                                    <td data-label="">City</td>
                                    <td data-label="City">MATHER</td>
                                    <td data-label="">State</td>
                                    <td data-label="State">CALIFORNIA</td>
                                </tr>
                                <tr class="devkit-table-row">
                                    <td data-label="">Zip Code</td>
                                    <td data-label="Zip Code">95655-4155</td>
                                    <td data-label="">Country</td>
                                    <td data-label="Country">UNITED STATES</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
                """;

        FaaAircraftRecord record = service.parseFaaResultHtml("N900AA", htmlSample);
        assertNotNull(record);
        assertTrue(record.isRecordFound());
        assertEquals("N900AA", record.getNNumber());
        assertEquals("2024 2246", record.getSerialNumber());
        assertEquals("Valid", record.getStatus());
        assertEquals("EVEKTOR-AEROTECHNIK A S", record.getManufacturer());
        assertEquals("HARMONY LSA", record.getModel());
        assertEquals("Fixed Wing Single-Engine", record.getTypeAircraft());
        assertEquals("4 Cycle", record.getTypeEngine());
        assertEquals("2024", record.getMfrYear());
        assertEquals("AC6F2D", record.getModeSCodeHex());
        assertEquals("12/17/2024", record.getCertificateIssueDate());
        assertEquals("12/31/2031", record.getExpirationDate());
        assertEquals("ERDMAN AVIATION LLC", record.getOwnerName());
        assertEquals("MATHER", record.getOwnerCity());
        assertEquals("CALIFORNIA", record.getOwnerState());
        assertEquals("95655-4155", record.getOwnerZip());
        assertEquals("UNITED STATES", record.getOwnerCountry());
    }

    @Test
    public void testParseFaaResultHtmlNotFound() {
        String notFoundHtml = "<html><body>No records found</body></html>";
        FaaAircraftRecord record = service.parseFaaResultHtml("N00000", notFoundHtml);
        assertNotNull(record);
        assertFalse(record.isRecordFound());
    }

    @Test
    public void testQueryIcaoNonUs() {
        FaaAircraftRecord record = service.queryIcao("400123");
        assertNotNull(record);
        assertFalse(record.isRecordFound());
        assertEquals("400123", record.getModeSCodeHex());
        assertTrue(record.getErrorMessage().contains("Non-US"));
    }

    @Test
    public void testCacheAndClear() {
        service.clearCache();
        assertTrue(service.getCachedRecords().isEmpty());
    }
}
