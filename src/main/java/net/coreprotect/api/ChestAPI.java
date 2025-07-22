package net.coreprotect.api;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.block.Block;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.WorldUtils;

public class ChestAPI {

    private ChestAPI() {
        throw new IllegalStateException("API class");
    }

    public static CompletableFuture<List<String[]>> performLookupAsync(Block block, int offset) {
        if (!Config.getGlobal().API_ENABLED || block == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        return CompletableFuture.supplyAsync(() -> performLookup(block, offset));
    }

    public static List<String[]> performLookup(Block block, int offset) {
        List<String[]> result = new ArrayList<>();

        if (!Config.getGlobal().API_ENABLED) {
            return result;
        }

        if (block == null) {
            return result;
        }

        try (Connection connection = Database.getConnection(false, 1000)) {
            if (connection == null) {
                return result;
            }

            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int time = (int) (System.currentTimeMillis() / 1000L);
            int worldId = WorldUtils.getWorldId(block.getWorld().getName());
            int checkTime = 0;

            if (offset > 0) {
                checkTime = time - offset;
            }

            try (Statement statement = connection.createStatement()) {
                String query = "SELECT time,user,action,type,data,amount,metadata,rolled_back FROM " + ConfigHandler.prefix + "container " + WorldUtils.getWidIndex("container") + "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND time > '" + checkTime + "' ORDER BY rowid DESC";

                try (ResultSet results = statement.executeQuery(query)) {
                    while (results.next()) {
                        String resultTime = results.getString("time");
                        int resultUserId = results.getInt("user");
                        String resultAction = results.getString("action");
                        int resultType = results.getInt("type");
                        String resultData = results.getString("data");
                        int resultAmount = results.getInt("amount");
                        byte[] resultMetadata = results.getBytes("metadata");
                        String resultRolledBack = results.getString("rolled_back");

                        if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                            UserStatement.loadName(connection, resultUserId);
                        }

                        String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                        String metadataString = "";
                        if (resultMetadata != null) {
                            metadataString = new String(resultMetadata, StandardCharsets.ISO_8859_1);
                        }

                        String[] lookupData = new String[] { resultTime, resultUser, String.valueOf(x), String.valueOf(y), String.valueOf(z), String.valueOf(resultType), resultData, resultAction, resultRolledBack, String.valueOf(worldId), String.valueOf(resultAmount), metadataString, "" };
                        result.add(lookupData);
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}