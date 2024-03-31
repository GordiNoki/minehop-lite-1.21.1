package net.nerdorg.minehop.entity.custom;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;

import java.util.HashMap;
import java.util.List;

public class EndEntity extends Zone {
    private BlockPos corner1;
    private BlockPos corner2;
    private String paired_map = "";

    public EndEntity(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (corner1 != null) {
            nbt.putInt("Corner1X", corner1.getX());
            nbt.putInt("Corner1Y", corner1.getY());
            nbt.putInt("Corner1Z", corner1.getZ());
        }
        if (corner2 != null) {
            nbt.putInt("Corner2X", corner2.getX());
            nbt.putInt("Corner2Y", corner2.getY());
            nbt.putInt("Corner2Z", corner2.getZ());
        }
        nbt.putString("map", paired_map);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        int x1 = nbt.getInt("Corner1X");
        int y1 = nbt.getInt("Corner1Y");
        int z1 = nbt.getInt("Corner1Z");
        corner1 = new BlockPos(x1, y1, z1);

        int x2 = nbt.getInt("Corner2X");
        int y2 = nbt.getInt("Corner2Y");
        int z2 = nbt.getInt("Corner2Z");
        corner2 = new BlockPos(x2, y2, z2);

        paired_map = nbt.getString("map");
    }

    public void setPairedMap(String paired_map) {
        this.paired_map = paired_map;
    }

    public void setCorner1(BlockPos corner1) {
        this.corner1 = corner1;
    }

    public void setCorner2(BlockPos corner2) {
        this.corner2 = corner2;
    }

    public String getPairedMap() {
        return paired_map;
    }

    public BlockPos getCorner1() {
        return corner1;
    }

    public BlockPos getCorner2() {
        return corner2;
    }

    public static DefaultAttributeContainer.Builder createResetEntityAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 1000000);
    }

    private void handleMapCompletion(ServerPlayerEntity player) {
        HashMap<String, Long> timerMap = Minehop.timerManager.get(player.getNameForScoreboard());
        List<String> keyList = timerMap.keySet().stream().toList();
        double rawTime = (double) (System.nanoTime() - timerMap.get(keyList.get(0))) / 1000000000;
        String formattedNumber = String.format("%.5f", rawTime);
        DataManager.RecordData mapRecord = DataManager.getRecord(this.paired_map);
        if (mapRecord != null) {
            if (rawTime < mapRecord.time) {
                Logger.logGlobal(this.getServer(), player.getNameForScoreboard() + " just beat " + mapRecord.name + "'s time (" + String.format("%.5f", mapRecord.time) + ") on " + mapRecord.map_name + " and now hold the world record with a time of " + formattedNumber + "!");
                Minehop.recordList.remove(mapRecord);
                Minehop.recordList.add(new DataManager.RecordData(player.getNameForScoreboard(), this.paired_map, rawTime));
                DataManager.saveRecordData(player.getServerWorld(), Minehop.recordList);
            }
        }
        else {
            Logger.logGlobal(this.getServer(), player.getNameForScoreboard() + " just claimed the world record on " + this.paired_map + " with a time of " + formattedNumber + "!");
            Minehop.recordList.add(new DataManager.RecordData(player.getNameForScoreboard(), this.paired_map, rawTime));
            DataManager.saveRecordData(player.getServerWorld(), Minehop.recordList);
        }
        DataManager.RecordData mapPersonalRecord = DataManager.getPersonalRecord(player.getNameForScoreboard(), this.paired_map);
        if (mapPersonalRecord != null) {
            if (rawTime < mapPersonalRecord.time) {
                Logger.logSuccess(player, "You just beat your time (" + mapPersonalRecord.time + ") on " + mapPersonalRecord.map_name + ", your new record is " + formattedNumber + "!");
                Minehop.personalRecordList.remove(mapPersonalRecord);
                Minehop.personalRecordList.add(new DataManager.RecordData(player.getNameForScoreboard(), this.paired_map, rawTime));
                DataManager.savePersonalRecordData(player.getServerWorld(), Minehop.personalRecordList);
            }
        }
        else {
            Logger.logSuccess(player, "You just claimed a personal record of " + formattedNumber + "!");
            Minehop.personalRecordList.add(new DataManager.RecordData(player.getNameForScoreboard(), this.paired_map, rawTime));
            DataManager.savePersonalRecordData(player.getServerWorld(), Minehop.personalRecordList);
        }
        Logger.logSuccess(player, "Completed " + this.paired_map + " in " + formattedNumber + " seconds.");
    }

    @Override
    public void tick() {
        World world = this.getWorld();
        if (world instanceof ServerWorld serverWorld) {
            if (serverWorld.getTime() % 100 == 0) {
                if (this.corner1 != null && this.corner2 != null) {
                    int avgX = (this.corner1.getX() + this.corner2.getX()) / 2;
                    int avgY = (this.corner1.getY() + this.corner2.getY()) / 2;
                    int avgZ = (this.corner1.getZ() + this.corner2.getZ()) / 2;

                    this.teleport(avgX, avgY, avgZ);
                }
                for (ServerPlayerEntity worldPlayer : serverWorld.getPlayers()) {
                    PacketHandler.updateZone(worldPlayer, this.getId(), this.corner1, this.corner2, this.paired_map, 0);
                }
            }
            if (this.corner1 != null && this.corner2 != null) {
                DataManager.MapData pairedMap = DataManager.getMap(this.paired_map);
                if (pairedMap != null) {
                    Box colliderBox = new Box(new Vec3d(this.corner1.getX(), this.corner1.getY(), this.corner1.getZ()), new Vec3d(this.corner2.getX(), this.corner2.getY(), this.corner2.getZ()));
                    List<ServerPlayerEntity> players = serverWorld.getPlayers();
                    for (ServerPlayerEntity player : players) {
                        if (!player.isCreative() && !player.isSpectator()) {
                            if (colliderBox.contains(player.getPos())) {
                                if (Minehop.timerManager.containsKey(player.getNameForScoreboard())) {
                                    if (!this.paired_map.equals("spawn")) {
                                        handleMapCompletion(player);
                                    }
                                    Minehop.timerManager.remove(player.getNameForScoreboard());
                                }
                            }
                        }
                    }
                }
                else {
                    this.kill();
                }
            }
        }
        super.tick();
    }
}
