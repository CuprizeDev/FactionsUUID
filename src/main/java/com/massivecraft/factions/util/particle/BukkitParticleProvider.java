package com.massivecraft.factions.util.particle;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public class BukkitParticleProvider implements ParticleProvider<Particle> {

    @Override
    public String name() {
        return "BUKKIT";
    }

    @Override
    public void spawn(Particle effect, Location location, int count) {
        if (effect.name().equals("BLOCK_MARKER")) {
            try {
                location.getWorld().spawnParticle(effect, location, count, 0, 0, 0, 0, Material.BARRIER.createBlockData());
                return;
            } catch (Throwable ignored) {
            }
        }
        location.getWorld().spawnParticle(effect, location, count);
    }

    @Override
    public void playerSpawn(Player player, Particle effect, Location location, int count) {
        if (effect.name().equals("BLOCK_MARKER")) {
            try {
                player.spawnParticle(effect, location, count, 0, 0, 0, 0, Material.BARRIER.createBlockData());
                return;
            } catch (Throwable ignored) {
            }
        }
        player.spawnParticle(effect, location, count);
    }

    @Override
    public void spawn(Particle particle, Location location, int count, double speed, double offsetX, double offsetY, double offsetZ) {
        if (particle.name().equals("BLOCK_MARKER")) {
            try {
                location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed, Material.BARRIER.createBlockData());
                return;
            } catch (Throwable ignored) {
            }
        }
        location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed);
    }

    @Override
    public void playerSpawn(Player player, Particle particle, Location location, int count, double speed, double offsetX, double offsetY, double offsetZ) {
        if (particle.name().equals("BLOCK_MARKER")) {
            try {
                player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed, Material.BARRIER.createBlockData());
                return;
            } catch (Throwable ignored) {
            }
        }
        player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed);
    }

    @Override
    public void spawn(Particle particle, Location location, ParticleColor color) {
        location.getWorld().spawnParticle(particle, location, 1, new Particle.DustOptions(color.getColor(), 1));
    }

    @Override
    public void playerSpawn(Player player, Particle particle, Location location, ParticleColor color) {
        player.spawnParticle(particle, location, 1, new Particle.DustOptions(color.getColor(), 1.5f));
    }

    @Override
    public Particle effectFromString(String string) {
        for (Particle particle : Particle.values()) {
            if (particle.name().equalsIgnoreCase(string)) {
                return particle;
            }
        }
        if (string.equalsIgnoreCase("BARRIER")) {
            for (Particle particle : Particle.values()) {
                if (particle.name().equals("BLOCK_MARKER")) {
                    return particle;
                }
            }
        }
        if (string.equalsIgnoreCase("REDSTONE")) {
            for (Particle particle : Particle.values()) {
                if (particle.name().equals("DUST")) {
                    return particle;
                }
            }
        }
        return null;
    }

    @Override
    public String effectName(Particle particle) {
        return particle.name();
    }
}
