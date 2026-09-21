package com.sugamflow.school.hostel.ops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HostelOpsCatalog {

  public static final String TYPE_BLOCK = "BLOCK";
  public static final String TYPE_ROOM_TYPE = "ROOM_TYPE";
  public static final String TYPE_ALLOCATION_POLICY = "ALLOCATION_POLICY";
  public static final String FEATURE_OPS_DEPTH = "FEATURE_OPS_DEPTH";

  private HostelOpsCatalog() {}

  public static List<Map<String, Object>> defaultBlocks() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("definitionKey", "main_block");
    b.put("name", "Main Hostel Block");
    b.put("floors", 4);
    b.put("enabled", true);
    list.add(b);
    return list;
  }

  public static List<Map<String, Object>> defaultRoomTypes() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("definitionKey", "twin_sharing");
    r.put("name", "Twin sharing");
    r.put("bedsPerRoom", 2);
    r.put("totalRooms", 20);
    r.put("monthlyFee", 4500);
    list.add(r);
    return list;
  }

  public static List<Map<String, Object>> defaultAllocationPolicies() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("definitionKey", "default_alloc");
    p.put("name", "Default allocation policy");
    p.put("maxOccupancyPercent", 95);
    p.put("waitlistEnabled", true);
    list.add(p);
    return list;
  }
}
