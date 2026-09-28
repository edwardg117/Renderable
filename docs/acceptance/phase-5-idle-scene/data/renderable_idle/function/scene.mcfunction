# Renderable's idle-cost scene (spec addendum §P4.A4 and §P5.C1). Run once, as the player, in a Classic Flat
# world whose surface is y -60: /function renderable_idle:scene
gamerule doDaylightCycle false
gamerule doMobSpawning false
gamerule doWeatherCycle false
time set noon
weather clear
kill @e[type=!player]
gamemode spectator @s
tp @s 0 -60 0 180 0
# Twenty villagers that never move, 5 across and 4 deep, 6 to 12 blocks in front of the camera (facing north).
summon minecraft:villager -4 -60 -6 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -2 -60 -6 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 0 -60 -6 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 2 -60 -6 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 4 -60 -6 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -4 -60 -8 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -2 -60 -8 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 0 -60 -8 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 2 -60 -8 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 4 -60 -8 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -4 -60 -10 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -2 -60 -10 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 0 -60 -10 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 2 -60 -10 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 4 -60 -10 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -4 -60 -12 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager -2 -60 -12 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 0 -60 -12 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 2 -60 -12 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
summon minecraft:villager 4 -60 -12 {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b}
