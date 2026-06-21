package com.whisent.ourthoughtsareone.entity;

import com.whisent.ourthoughtsareone.core.FlightManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class FlightLinkEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(FlightLinkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(FlightLinkEntity.class, EntityDataSerializers.INT);

    private UUID ownerUUID;
    private UUID targetUUID;
    private Vec3 lastOwnerPoint = Vec3.ZERO;
    private Vec3 lastTargetPoint = Vec3.ZERO;
    private boolean hasLastLinkPoints;

    public FlightLinkEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void setup(ServerPlayer owner, ServerPlayer target) {
        ownerUUID = owner.getUUID();
        targetUUID = target.getUUID();
        entityData.set(OWNER_ID, owner.getId());
        entityData.set(TARGET_ID, target.getId());
        syncToMiddle(owner, target);
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setNoGravity(true);

        if (level().isClientSide()) {
            Entity owner = getSyncedOwner();
            Entity target = getSyncedTarget();
            if (owner != null && target != null) {
                syncToMiddle(owner, target);
            }
            return;
        }

        if (!(level() instanceof ServerLevel level)) {
            discard();
            return;
        }
        if (ownerUUID == null || targetUUID == null) {
            discard();
            return;
        }
        if (!(level.getPlayerInAnyDimension(ownerUUID) instanceof ServerPlayer owner) || !(level.getPlayerInAnyDimension(targetUUID) instanceof ServerPlayer target)) {
            discard();
            return;
        }
        if (!FlightManager.isLinked(owner, target)) {
            discard();
            return;
        }
        syncToMiddle(owner, target);
    }

    private void syncToMiddle(Entity owner, Entity target) {
        Vec3 from = linkPoint(owner);
        Vec3 to = linkPoint(target);
        if (!isValidPoint(from) || !isValidPoint(to)) {
            return;
        }
        lastOwnerPoint = from;
        lastTargetPoint = to;
        hasLastLinkPoints = true;
        Vec3 mid = from.add(to).scale(0.5);
        absSnapTo(mid.x, mid.y, mid.z, 0.0F, 0.0F);
        setDeltaMovement(Vec3.ZERO);
    }

    private boolean isValidPoint(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private Vec3 linkPoint(Entity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.65, 0.0);
    }

    public int getSyncedOwnerId() {
        return entityData.get(OWNER_ID);
    }

    public int getSyncedTargetId() {
        return entityData.get(TARGET_ID);
    }

    private Entity getSyncedOwner() {
        int id = entityData.get(OWNER_ID);
        return id < 0 ? null : level().getEntity(id);
    }

    private Entity getSyncedTarget() {
        int id = entityData.get(TARGET_ID);
        return id < 0 ? null : level().getEntity(id);
    }

    public Vec3 getOwnerLinkPoint(float partialTicks) {
        Entity owner = getSyncedOwner();
        if (owner != null) {
            Vec3 point = linkPoint(owner, partialTicks);
            if (isValidPoint(point)) {
                lastOwnerPoint = point;
                return point;
            }
        }
        return lastOwnerPoint;
    }

    public Vec3 getTargetLinkPoint(float partialTicks) {
        Entity target = getSyncedTarget();
        if (target != null) {
            Vec3 point = linkPoint(target, partialTicks);
            if (isValidPoint(point)) {
                lastTargetPoint = point;
                return point;
            }
        }
        return lastTargetPoint;
    }

    private Vec3 linkPoint(Entity entity, float partialTicks) {
        double x = net.minecraft.util.Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double y = net.minecraft.util.Mth.lerp(partialTicks, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.65;
        double z = net.minecraft.util.Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        return new Vec3(x, y, z);
    }

    public boolean hasUsableLinkPoints() {
        return hasLastLinkPoints;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_ID, -1);
        builder.define(TARGET_ID, -1);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return false;
    }
}
