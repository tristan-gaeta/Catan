# References

## Game rules

* **CATAN – The Game, rulebook (6th edition, 2025).** The source for the base rules, the variable setup, the frame
  pieces and harbor slots, the A–R number discs, and the roll for first player.
  [catan.com rulebook PDF](https://www.catan.com/sites/default/files/2025-03/CN3081%20CATAN%E2%80%93The%20Game%20Rulebook%20secure%20%281%29.pdf),
  linked from [catan.com/understand-catan/game-rules](https://www.catan.com/understand-catan/game-rules).

## Hex grids

Amit Patel's Red Blob Games articles are the basis of the board geometry:

* [Hexagonal Grids](https://www.redblobgames.com/grids/hexagons/), in particular:
  * [axial coordinates](https://www.redblobgames.com/grids/hexagons/#coordinates-axial) for `AxialCoord` and `Hex`;
  * [neighbors](https://www.redblobgames.com/grids/hexagons/#neighbors-axial) for `Direction`;
  * [distances](https://www.redblobgames.com/grids/hexagons/#distances-axial) for `AxialCoord.length`;
  * [rotation](https://www.redblobgames.com/grids/hexagons/#rotation) for `rotateCC`;
  * [spiral rings](https://www.redblobgames.com/grids/hexagons/#rings-spiral) for `Hex.spiralInwardCC`;
  * [map storage](https://www.redblobgames.com/grids/hexagons/#map-storage) for the padded `Hex`, `Vertex` and `Edge`
    arrays;
  * [hex to pixel](https://www.redblobgames.com/grids/hexagons/#hex-to-pixel-axial) for drawing, as in the diagrams.
* [Grid parts and relationships](https://www.redblobgames.com/grids/parts/): how faces, edges and vertices relate, as
  in `BoardGraph`.

## Randomness

* David Blackman and Sebastiano Vigna, *Scrambled Linear Pseudorandom Number Generators*
  ([arXiv:1805.01407](https://arxiv.org/abs/1805.01407)). Defines xoshiro128++, which `GameRandom` implements.
  Reference C code is at [prng.di.unimi.it](https://prng.di.unimi.it/).
* Guy Steele, Doug Lea and Christine Flood, *Fast Splittable Pseudorandom Number Generators* (OOPSLA 2014). Defines
  SplitMix64, used for seeding.
* Daniel Lemire, *Fast Random Integer Generation in an Interval*
  ([arXiv:1805.10941](https://arxiv.org/abs/1805.10941)). The unbiased bounded draw in `GameRandom.nextInt(bound)`.
* Fisher–Yates shuffle (Durstenfeld's in-place version), used in `shuffleWith`.

## Kotlin

* [Inline value classes](https://kotlinlang.org/docs/inline-classes.html) for `ResourceCounts`, `DevCardCounts` and
  `AxialCoord`.
* [`enumEntries`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.enums/enum-entries.html), used by the codec's
  reified `code` helpers.
* [KDoc syntax](https://kotlinlang.org/docs/kotlin-doc.html), for the documentation comments that link here.
