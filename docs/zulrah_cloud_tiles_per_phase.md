# Zulrah cloud tiles per phase (from the tsps / Near-Reality data)

Generated from `ZulrahRotations.js` of the RSPSApp/tsps port. Unverified against the real game.
Maps use the wiki diagram view: **south at the top, east on the left, west on the right**.
`#` tile covered by a cloud, `.` free platform tile, `x` a wiki tile-marker spot that is clear,
`X` a wiki tile-marker spot under a cloud, blank = water or pillar. Only this phase's own barrages
are shown; clouds from the previous phase can still be down (see the note on duration).

Cloud lattice (centres, 3x3 each): bottom B1 (2266,3069) B2 (2269,3069) B3 (2272,3069);
west arm W1 (2263,3070) W2 (2263,3073) W3 (2263,3076); east arm E1 (2273,3072) E2 (2273,3075) E3 (2273,3078).
B3' (2272,3070) is a variant that only the opening / closing barrages use.

### Phase 1 of every rotation (opening, and the closing phase of the last rotation): 4 barrages

Centre, green  four barrages (the closing phase has 5 ranged first)

Clouds: B2(2269, 3069), B3'(2272, 3070), B1(2266, 3069), W1(2263, 3070), E1(2273, 3072), E2(2273, 3075), W2(2263, 3073), W3(2263, 3076)
Wiki x spots: start under cloud; W pillar under cloud; E pillar under cloud; E tip a clear; E tip b clear

```
    #####
  ####X####
 ############
###       ###
##X       X##
###       ###
###       ###
###       ###
###       ###
x..       ##
  x       .
```
Free platform tiles: 5 of 69

## Rotation 1 (Crimson A)

### Phase 2: middle red (after the opening fill)

No clouds of its own. Which of phase 1's clouds are still down depends on how long
a cloud lasts; the last two barrages of phase 1 sit on E1/E2 and W2/W3, which cover the
pillar x spots (2264,3072) and (2272,3072) that the diagram shows for the tail swing.

### Phase 4: south green

South, green  order: 5 ranged -> orb -> orb -> CLOUDS -> CLOUDS -> orb -> orb

Clouds: W1(2263, 3070), B1(2266, 3069), B3(2272, 3069), E1(2273, 3072)
Snakeling tiles: (2263, 3076), (2263, 3073), (2273, 3075), (2273, 3077)
Wiki x spots: start clear; W pillar clear; E pillar under cloud; E tip a clear; E tip b clear

```
    ...##
  ##..x####
 ###...######
###       ###
##X       x..
###       ...
...       ...
...       ...
...       ...
x..       ..
  x       .
```
Free platform tiles: 40 of 69

### Phase 7: south green

South, green  order: CLOUDS -> CLOUDS -> CLOUDS -> orb -> orb -> orb -> orb

Clouds: B2(2269, 3069), B3(2272, 3069), W1(2263, 3070), B1(2266, 3069), W2(2263, 3073), W3(2263, 3076)
Snakeling tiles: (2272, 3071), (2273, 3075), (2273, 3077), (2273, 3072)
Wiki x spots: start under cloud; W pillar under cloud; E pillar clear; E tip a clear; E tip b clear

```
    #####
  ####X####
 ############
...       ###
..x       X##
...       ###
...       ###
...       ###
...       ###
x..       ##
  x       .
```
Free platform tiles: 23 of 69

### Phase 8: south blue

South, blue  order: 5 magic -> orb -> CLOUDS -> orb -> CLOUDS -> orb

Clouds: B1(2266, 3069), B2(2269, 3069), B3(2272, 3069), E1(2273, 3072)
Snakeling tiles: (2263, 3070), (2263, 3076), (2263, 3073)
Wiki x spots: start under cloud; W pillar clear; E pillar under cloud; E tip a clear; E tip b clear

```
    #####
  ####X###.
 #########...
###       ...
##X       x..
###       ...
...       ...
...       ...
...       ...
x..       ..
  x       .
```
Free platform tiles: 38 of 69

### Phase 9: west green

West, green  order: 10 ranged/alt -> CLOUDS -> CLOUDS -> CLOUDS -> CLOUDS

Clouds: W1(2263, 3070), B1(2266, 3069), B2(2269, 3069), B3(2272, 3069), W2(2263, 3073), W3(2263, 3076), E1(2273, 3072), E2(2273, 3075)
Wiki x spots: start under cloud; W pillar under cloud; E pillar under cloud; E tip a clear; E tip b clear

```
    #####
  ####X####
 ############
###       ###
##X       X##
###       ###
###       ###
###       ###
###       ###
x..       ##
  x       .
```
Free platform tiles: 5 of 69

## Rotation 2 (Crimson B)

### Phase 2: middle red (after the opening fill)

No clouds of its own. Which of phase 1's clouds are still down depends on how long
a cloud lasts; the last two barrages of phase 1 sit on E1/E2 and W2/W3, which cover the
pillar x spots (2264,3072) and (2272,3072) that the diagram shows for the tail swing.

### Phase 4: west green

West, green  order: CLOUDS -> CLOUDS -> CLOUDS -> orb -> orb -> orb -> orb

Clouds: E1(2273, 3072), B3(2272, 3069), E2(2273, 3075), E3(2273, 3078), B2(2269, 3069), B1(2266, 3069)
Snakeling tiles: (2266, 3069), (2263, 3070), (2263, 3073), (2263, 3076)
Wiki x spots: start under cloud; W pillar clear; E pillar under cloud; E tip a under cloud; E tip b under cloud

```
    #####
  ####X###.
 #########...
###       ...
##X       x..
###       ...
###       ...
###       ...
###       ...
X##       ..
  X       .
```
Free platform tiles: 25 of 69

### Phase 5: south blue

South, blue  order: 5 magic -> orb -> orb -> CLOUDS -> CLOUDS -> orb -> orb

Clouds: W1(2263, 3070), B1(2266, 3069), B3(2272, 3069), E1(2273, 3072)
Snakeling tiles: (2263, 3076), (2263, 3073), (2273, 3075), (2273, 3077)
Wiki x spots: start clear; W pillar clear; E pillar under cloud; E tip a clear; E tip b clear

```
    ...##
  ##..x####
 ###...######
###       ###
##X       x..
###       ...
...       ...
...       ...
...       ...
x..       ..
  x       .
```
Free platform tiles: 40 of 69

### Phase 8: south blue

South, blue  order: 5 magic -> orb -> CLOUDS -> orb -> CLOUDS -> orb

Clouds: B1(2266, 3069), B2(2269, 3069), B3(2272, 3069), E1(2273, 3072)
Snakeling tiles: (2263, 3070), (2263, 3076), (2263, 3073)
Wiki x spots: start under cloud; W pillar clear; E pillar under cloud; E tip a clear; E tip b clear

```
    #####
  ####X###.
 #########...
###       ...
##X       x..
###       ...
...       ...
...       ...
...       ...
x..       ..
  x       .
```
Free platform tiles: 38 of 69

### Phase 9: west green

West, green  order: 10 ranged/alt -> CLOUDS -> CLOUDS -> CLOUDS -> CLOUDS

Clouds: W1(2263, 3070), B1(2266, 3069), B2(2269, 3069), B3(2272, 3069), W2(2263, 3073), W3(2263, 3076), E1(2273, 3072), E2(2273, 3075)
Wiki x spots: start under cloud; W pillar under cloud; E pillar under cloud; E tip a clear; E tip b clear

```
    #####
  ####X####
 ############
###       ###
##X       X##
###       ###
###       ###
###       ###
###       ###
x..       ##
  x       .
```
Free platform tiles: 5 of 69

## Rotation 3 (Serp)

### Phase 2: east green (after the opening fill)

No clouds of its own. Which of phase 1's clouds are still down depends on how long
a cloud lasts; the last two barrages of phase 1 sit on E1/E2 and W2/W3, which cover the
pillar x spots (2264,3072) and (2272,3072) that the diagram shows for the tail swing.

### Phase 3: middle red

Middle, red  order: CLOUDS -> orb -> CLOUDS -> orb -> CLOUDS -> orb -> melee x2

Clouds: E3(2273, 3078), E2(2273, 3075), B3'(2272, 3070), B2(2269, 3069), W1(2263, 3070), W2(2263, 3073)
Snakeling tiles: (2273, 3072), (2266, 3069), (2263, 3076)
Wiki x spots: start under cloud; W pillar under cloud; E pillar clear; E tip a under cloud; E tip b under cloud

```
    ###..
  ####X...#
 ######...###
.##       ###
..x       X##
...       ###
###       ###
###       ...
###       ...
X##       ..
  X       .
```
Free platform tiles: 24 of 69

### Phase 7: middle green

Middle, green  order: CLOUDS -> CLOUDS -> CLOUDS -> orb -> orb -> orb

Clouds: E3(2273, 3078), E2(2273, 3075), E1(2273, 3072), B3(2272, 3069), B2(2269, 3069), B1(2266, 3069)
Snakeling tiles: (2263, 3070), (2263, 3076), (2263, 3073)
Wiki x spots: start under cloud; W pillar clear; E pillar under cloud; E tip a under cloud; E tip b under cloud

```
    #####
  ####X###.
 #########...
###       ...
##X       x..
###       ...
###       ...
###       ...
###       ...
X##       ..
  X       .
```
Free platform tiles: 25 of 69

### Phase 9: middle blue

Middle, blue  order: 5 magic -> CLOUDS -> CLOUDS -> orb -> orb -> orb

Clouds: W3(2263, 3076), W2(2263, 3073), W1(2263, 3070), B1(2266, 3069)
Snakeling tiles: (2269, 3069), (2272, 3069), (2273, 3072)
Wiki x spots: start clear; W pillar under cloud; E pillar clear; E tip a clear; E tip b clear

```
    ...##
  ....x####
 ......######
...       ###
..x       X##
...       ###
...       ###
...       ###
...       ###
x..       ##
  x       .
```
Free platform tiles: 37 of 69

## Rotation 4 (Tanz)

### Phase 2: east blue (after the opening fill)

No clouds of its own. Which of phase 1's clouds are still down depends on how long
a cloud lasts; the last two barrages of phase 1 sit on E1/E2 and W2/W3, which cover the
pillar x spots (2264,3072) and (2272,3072) that the diagram shows for the tail swing.

### Phase 3: south green

South, green  order: 4 ranged -> CLOUDS -> CLOUDS

Clouds: W1(2263, 3070), B2(2269, 3069), B1(2266, 3069), B3(2272, 3069)
Wiki x spots: start under cloud; W pillar clear; E pillar clear; E tip a clear; E tip b clear

```
    #####
  ####X####
 ############
...       ###
..x       x..
...       ...
...       ...
...       ...
...       ...
x..       ..
  x       .
```
Free platform tiles: 40 of 69

### Phase 5: middle red

Middle, red  order: melee x2 -> CLOUDS -> CLOUDS

Clouds: W1(2263, 3070), B2(2269, 3069), B1(2266, 3069), B3(2272, 3069)
Wiki x spots: start under cloud; W pillar clear; E pillar clear; E tip a clear; E tip b clear

```
    #####
  ####X####
 ############
...       ###
..x       x..
...       ...
...       ...
...       ...
...       ...
x..       ..
  x       .
```
Free platform tiles: 40 of 69

### Phase 7: south green

South, green  order: orb -> orb -> orb -> orb -> orb -> orb -> CLOUDS -> CLOUDS -> CLOUDS

Clouds: E2(2273, 3075), E3(2273, 3078), B3(2272, 3069), E1(2273, 3072), B2(2269, 3069), B1(2266, 3069)
Snakeling tiles: (2263, 3076), (2263, 3073), (2263, 3070), (2273, 3072), (2273, 3075), (2273, 3078)
Wiki x spots: start under cloud; W pillar clear; E pillar under cloud; E tip a under cloud; E tip b under cloud

```
    #####
  ####X###.
 #########...
###       ...
##X       x..
###       ...
###       ...
###       ...
###       ...
X##       ..
  X       .
```
Free platform tiles: 25 of 69

### Phase 10: middle blue

Middle, blue  order: 4 magic -> CLOUDS -> CLOUDS -> CLOUDS

Clouds: W2(2263, 3073), B1(2266, 3069), W1(2263, 3070), W3(2263, 3076), B2(2269, 3069), B3(2272, 3069)
Wiki x spots: start under cloud; W pillar under cloud; E pillar clear; E tip a clear; E tip b clear

```
    #####
  ####X####
 ############
...       ###
..x       X##
...       ###
...       ###
...       ###
...       ###
x..       ##
  x       .
```
Free platform tiles: 23 of 69

