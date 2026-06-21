package com.whisent.ourthoughtsareone.entity;

import com.whisent.ourthoughtsareone.core.ProjectionManager;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class ProjectionAnchorEntity extends Entity {
    private static final EntityDataAccessor<Integer> RIDER_ID = SynchedEntityData.defineId(ProjectionAnchorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOUNT_ID = SynchedEntityData.defineId(ProjectionAnchorEntity.class, EntityDataSerializers.INT);

    private UUID riderUUID;
    private UUID mountUUID;
    private boolean initialMountTried;

    public ProjectionAnchorEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void setup(ServerPlayer rider, ServerPlayer mount) {
        riderUUID = rider.getUUID();
        mountUUID = mount.getUUID();
        entityData.set(RIDER_ID, rider.getId());
        entityData.set(MOUNT_ID, mount.getId());
        syncToMount(mount);
    }

    public UUID getRiderUUID() {
        return riderUUID;
    }

    public UUID getMountUUID() {
        return mountUUID;
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setNoGravity(true);

        if (level().isClientSide()) {
            Entity mount = getSyncedMount();
            if (mount != null) syncToMount(mount);
            return;
        }
        if (!(level() instanceof ServerLevel level)) {
            discard();
            return;
        }
        if (riderUUID == null || mountUUID == null) {
            discard();
            return;
        }

        if (!(level.getPlayerInAnyDimension(riderUUID) instanceof ServerPlayer rider) || !(level.getPlayerInAnyDimension(mountUUID) instanceof ServerPlayer mount)) {
            discard();
            return;
        }
        if (rider == null || mount == null || !rider.isAlive() || !mount.isAlive()) {
            if (rider != null) ProjectionManager.endProjection(rider, ProjectionManager.DisconnectReason.OTHER);
            discard();
            return;
        }
        if (!rider.level().dimension().equals(mount.level().dimension())) {
            ProjectionManager.endProjection(rider, ProjectionManager.DisconnectReason.OTHER);
            discard();
            return;
        }
        if (!ProjectionManager.isProjecting(rider) || ProjectionManager.getMount(rider, level) != mount) {
            discard();
            return;
        }
        syncToMount(mount);

        if (rider.getVehicle() != this) {
            if (!initialMountTried) {
                initialMountTried = true;
                boolean success = rider.startRiding(this, true, true);
                if (success) return;
            }
            ProjectionManager.endProjection(rider, ProjectionManager.DisconnectReason.OTHER);
            discard();
            return;
        }
    }

    private void syncToMount(Entity mount) {
        Vec3 pos = getAnchorPosition(mount);
        absSnapTo(pos.x, pos.y, pos.z, mount.getYRot(), mount.getXRot());
        setDeltaMovement(mount.getDeltaMovement());
        setYRot(mount.getYRot());
        setXRot(mount.getXRot());
        setYBodyRot(mount.getYRot());
        setYHeadRot(mount.getYRot());
    }

    private Vec3 getAnchorPosition(Entity mount) {
        return new Vec3(mount.getX(), mount.getEyeY() - 0.25, mount.getZ());
    }

    private Entity getSyncedMount() {
        int mountId = entityData.get(MOUNT_ID);
        return mountId < 0 ? null : level().getEntity(mountId);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RIDER_ID, -1);
        builder.define(MOUNT_ID, -1);
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

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        Entity mount = getSyncedMount();
        Vec3 anchorPos = mount == null ? position() : getAnchorPosition(mount);
        Vec3 passengerVehicleOffset = passenger.getVehicleAttachmentPoint(this);
        return anchorPos.add(passengerVehicleOffset);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return riderUUID != null && passenger.getUUID().equals(riderUUID) && getPassengers().isEmpty();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide()) {
            if (passenger instanceof Player player && ProjectionManager.isProjecting(player)) {
                ProjectionManager.endProjection(player, ProjectionManager.DisconnectReason.OTHER);
            }
            discard();
        }
    }
}
