package com.whisent.ourthoughtsareone.entity;

import com.whisent.ourthoughtsareone.core.FlightManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public class FlightLinkEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(FlightLinkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(FlightLinkEntity.class, EntityDataSerializers.INT);

    private UUID ownerUUID;
    private UUID targetUUID;

    // Client-side: true only when both endpoints were resolved this tick
    private boolean endpointsValid;
    private Vec3 ownerPoint = Vec3.ZERO;
    private Vec3 targetPoint = Vec3.ZERO;

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
        updatePosition(owner, target);
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setNoGravity(true);

        if (level().isClientSide()) {
            tickClient();
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
        updatePosition(owner, target);
    }

    private void tickClient() {
        Entity owner = getClientEntity(entityData.get(OWNER_ID));
        Entity target = getClientEntity(entityData.get(TARGET_ID));
        if (owner != null && target != null) {
            Vec3 from = computeLinkPoint(owner);
            Vec3 to = computeLinkPoint(target);
            if (isFinite(from) && isFinite(to)) {
                ownerPoint = from;
                targetPoint = to;
                endpointsValid = true;
                absSnapTo(from.x, from.y, from.z, 0.0F, 0.0F);
                setDeltaMovement(Vec3.ZERO);
                return;
            }
        }
        // This tick we couldn't resolve both endpoints — mark invalid
        endpointsValid = false;
    }

    private void updatePosition(Entity owner, Entity target) {
        Vec3 from = computeLinkPoint(owner);
        Vec3 to = computeLinkPoint(target);
        if (!isFinite(from) || !isFinite(to)) return;
        ownerPoint = from;
        targetPoint = to;
        endpointsValid = true;
        absSnapTo(from.x, from.y, from.z, 0.0F, 0.0F);
        setDeltaMovement(Vec3.ZERO);
    }

    // --- Public accessors for the renderer ---

    public boolean areEndpointsValid() {
        return endpointsValid;
    }

    /**
     * Returns the interpolated owner link point, or null if endpoints are not valid this tick.
     */
    @Nullable
    public Vec3 getOwnerLinkPoint(float partialTicks) {
        if (!endpointsValid) return null;
        Entity owner = getClientEntity(entityData.get(OWNER_ID));
        if (owner != null) {
            Vec3 point = computeLinkPointLerp(owner, partialTicks);
            if (isFinite(point)) return point;
        }
        // Fallback to last known good position from this tick
        return isFinite(ownerPoint) ? ownerPoint : null;
    }

    /**
     * Returns the interpolated target link point, or null if endpoints are not valid this tick.
     */
    @Nullable
    public Vec3 getTargetLinkPoint(float partialTicks) {
        if (!endpointsValid) return null;
        Entity target = getClientEntity(entityData.get(TARGET_ID));
        if (target != null) {
            Vec3 point = computeLinkPointLerp(target, partialTicks);
            if (isFinite(point)) return point;
        }
        return isFinite(targetPoint) ? targetPoint : null;
    }

    /**
     * Returns the beam offset vector (target - owner), or null if not valid.
     */
    @Nullable
    public Vec3 getBeamOffset(float partialTicks) {
        Vec3 from = getOwnerLinkPoint(partialTicks);
        Vec3 to = getTargetLinkPoint(partialTicks);
        if (from == null || to == null) return null;
        Vec3 offset = to.subtract(from);
        return isFinite(offset) ? offset : null;
    }

    /**
     * Returns the AABB that encloses the beam, for culling. Falls back to entity bounding box.
     */
    public AABB getBeamBounds(float partialTicks) {
        Vec3 from = getOwnerLinkPoint(partialTicks);
        Vec3 to = getTargetLinkPoint(partialTicks);
        if (from == null || to == null) {
            return getBoundingBox().inflate(2.0);
        }
        return new AABB(from, to).inflate(1.0);
    }

    // --- Internal helpers ---

    @Nullable
    private Entity getClientEntity(int id) {
        return id < 0 ? null : level().getEntity(id);
    }

    private Vec3 computeLinkPoint(Entity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.65, 0.0);
    }

    private Vec3 computeLinkPointLerp(Entity entity, float partialTicks) {
        double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.65;
        double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        return new Vec3(x, y, z);
    }

    private static boolean isFinite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    // --- Synched data ---

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

    // --- Non-interactive entity overrides ---

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
