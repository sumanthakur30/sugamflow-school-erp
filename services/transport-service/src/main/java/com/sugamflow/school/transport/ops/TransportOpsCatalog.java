package com.sugamflow.school.transport.ops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TransportOpsCatalog {

  public static final String TYPE_ROUTE = "ROUTE";
  public static final String TYPE_FARE_SLAB = "FARE_SLAB";
  public static final String TYPE_VEHICLE = "VEHICLE";
  public static final String FEATURE_OPS_DEPTH = "FEATURE_OPS_DEPTH";

  private TransportOpsCatalog() {}

  public static List<Map<String, Object>> defaultRoutes() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("definitionKey", "route_a");
    r.put("name", "City Center Route A");
    r.put("distanceKm", 12);
    r.put("stops", List.of("Campus", "Market", "Station"));
    list.add(r);
    return list;
  }

  public static List<Map<String, Object>> defaultFareSlabs() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> f = new LinkedHashMap<>();
    f.put("definitionKey", "default_fare");
    f.put("name", "Default fare slab");
    f.put("baseAmount", 500);
    f.put("perKmRate", 12);
    f.put("currency", "INR");
    list.add(f);
    return list;
  }

  public static List<Map<String, Object>> defaultVehicles() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("definitionKey", "bus_01");
    v.put("name", "Bus 01");
    v.put("registrationNo", "DL-01-AB-1234");
    v.put("capacity", 40);
    v.put("enabled", true);
    list.add(v);
    return list;
  }
}
