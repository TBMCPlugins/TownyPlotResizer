# TownyPlotResizer
This Java application can change the Towny block size changing the claims as well as many configuration options as needed.

*It is* ***very*** *important that it's been only tested with 16->8 and 8->16 and the latter* ***fails.*** *It may not work at all in some cases. Always backup Towny data before using this.*

Known bugs:
* 8->16 doesn't work, smaller to larger conversions fail
* nation_level.townBlockLimitBonus should be updated
* price_purchased_bonus_townblock_increase shouldn't be updated

I don't intend to develop it further than our needs but PRs are welcome.
