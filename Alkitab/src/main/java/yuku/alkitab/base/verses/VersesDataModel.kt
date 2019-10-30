package yuku.alkitab.base.verses

import yuku.alkitab.base.util.AppLog
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari

private const val TAG = "VersesDataModel"

class VersesDataModel(
    val ari_bc_: Int,
    val verses_: SingleChapterVerses,
    val pericopeBlockCount_: Int,
    val pericopeAris_: IntArray,
    val pericopeBlocks_: Array<PericopeBlock>,
    val version_: Version?,
    val versionId_: String?,
    val textSizeMult_: Float,
    val versesAttributes: VersesAttributes
) {
    /**
     * For each element, if 0 or more, it refers to the 0-based verse number.
     * If negative, -1 is the index 0 of pericope, -2 (a) is index 1 (b) of pericope, etc.
     *
     * Convert a to b: b = -a-1;
     * Convert b to a: a = -b-1;
     */
    private val itemPointer_: IntArray by lazy {
        val nverse = verses_.verseCount
        val res = IntArray(nverse + pericopeBlockCount_)

        var pos_block = 0
        var pos_verse = 0
        var pos_itemPointer = 0

        while (true) {
            // check if we still have pericopes remaining
            if (pos_block < pericopeBlockCount_) {
                // still possible
                if (Ari.toVerse(pericopeAris_[pos_block]) - 1 == pos_verse) {
                    // We have a pericope.
                    res[pos_itemPointer++] = -pos_block - 1
                    pos_block++
                    continue
                }
            }

            // check if there is no verses remaining
            if (pos_verse >= nverse) {
                break
            }

            // there is no more pericopes, OR not the time yet for pericopes. So we insert a verse.
            res[pos_itemPointer++] = pos_verse
            pos_verse++
        }

        if (res.size != pos_itemPointer) {
            throw RuntimeException("Algorithm to insert pericopes error!! pos_itemPointer=$pos_itemPointer pos_verse=$pos_verse pos_block=$pos_block nverse=$nverse pericopeBlockCount_=$pericopeBlockCount_ pericopeAris_:${pericopeAris_.contentToString()} pericopeBlocks_:${pericopeBlocks_.contentToString()}")
        }

        res
    }

    val itemCount get() = itemPointer_.size

    /**
     * For example, when pos=0 is a pericope and pos=1 is the first verse,
     * this method returns 0.
     *
     * @return position on this adapter, or -1 if not found
     */
    fun getPositionOfPericopeBeginningFromVerse(verse_1: Int): Int {
        val verse_0 = verse_1 - 1

        var i = 0
        val len = itemPointer_.size
        while (i < len) {
            if (itemPointer_[i] == verse_0) {
                // we've found it, but if we can move back to pericopes, it is better.
                for (j in i - 1 downTo 0) {
                    if (itemPointer_[j] < 0) {
                        // it's still pericope, so let's continue
                        i = j
                    } else {
                        // no longer a pericope (means, we are on the previous verse)
                        break
                    }
                }
                return i
            }
            i++
        }

        return -1
    }

    /**
     * Let's say pos 0 is pericope and pos 1 is verse_1 1;
     * then this method called with verse_1=1 returns 1.
     *
     * @return position or -1 if not found
     */
    fun getPositionIgnoringPericopeFromVerse(verse_1: Int): Int {
        val verse_0 = verse_1 - 1

        var i = 0
        val len = itemPointer_.size
        while (i < len) {
            if (itemPointer_[i] == verse_0) return i
            i++
        }

        return -1
    }

    /**
     * @return verse_1 or 0 if doesn't make sense
     */
    fun getVerseFromPosition(position: Int): Int {
        var position = position

        if (position >= itemPointer_.size) {
            position = itemPointer_.size - 1
        }

        var id = itemPointer_[position]

        if (id >= 0) {
            return id + 1
        }

        // it's a pericope. Let's move forward until we get a verse
        for (i in position + 1 until itemPointer_.size) {
            id = itemPointer_[i]

            if (id >= 0) {
                return id + 1
            }
        }

        AppLog.w(TAG, "pericope title at the last position? does not make sense.")
        return 0
    }

    /**
     * Similar to [.getVerseFromPosition], but returns 0 if the specified position is a pericope or doesn't make sense.
     */
    fun getVerseOrPericopeFromPosition(position: Int): Int {
        if (position < 0 || position >= itemPointer_.size) {
            return 0
        }

        val id = itemPointer_[position]

        return if (id >= 0) {
            id + 1
        } else {
            0
        }
    }

    fun getVerseText(verse_1: Int): String? {
        return if (verse_1 in 1..verses_.verseCount) {
            verses_.getVerse(verse_1 - 1)
        } else {
            null
        }
    }

    fun isEnabled(position: Int): Boolean {
        // guard against wild ListView.onInitializeAccessibilityNodeInfoForItem
        return when {
            position >= itemPointer_.size -> false
            itemPointer_[position] >= 0 -> true
            else -> false
        }
    }

    companion object {
        @JvmField
        val EMPTY = VersesDataModel(
            ari_bc_ = 0,
            verses_ = SingleChapterVerses.EMPTY,
            pericopeBlockCount_ = 0,
            pericopeAris_ = IntArray(0),
            pericopeBlocks_ = emptyArray(),
            version_ = null,
            versionId_ = null,
            textSizeMult_ = 1f,
            versesAttributes = VersesAttributes.EMPTY
        )
    }
}