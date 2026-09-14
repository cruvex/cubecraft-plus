package com.cruvex.cubecraftplus.model;

import com.google.gson.annotations.SerializedName;

public record ChestLocation(@SerializedName("season_name") String seasonName, int x, int y, int z) {

}
