package io.getstream.chat.android.ui.channel.list

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.getstream.chat.android.client.logger.ChatLogger
import io.getstream.chat.android.client.models.Channel
import io.getstream.chat.android.client.models.User
import io.getstream.chat.android.ui.channel.actions.ChannelActionsDialogFragment
import io.getstream.chat.android.ui.channel.list.ChannelListView.ChannelClickListener
import io.getstream.chat.android.ui.channel.list.ChannelListView.UserClickListener
import io.getstream.chat.android.ui.channel.list.adapter.ChannelListItemAdapter
import io.getstream.chat.android.ui.channel.list.adapter.viewholder.ChannelItemSwipeListener
import io.getstream.chat.android.ui.channel.list.adapter.viewholder.ChannelListItemViewHolderFactory
import io.getstream.chat.android.ui.channel.list.adapter.viewholder.SwipeViewHolder
import io.getstream.chat.android.ui.utils.extensions.cast
import io.getstream.chat.android.ui.utils.extensions.getFragmentManager
import io.getstream.chat.android.ui.utils.extensions.isDirectMessaging

public class ChannelListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    private var endReachedListener: EndReachedListener? = null
    private val layoutManager: ScrollPauseLinearLayoutManager
    private val scrollListener: EndReachedScrollListener = EndReachedScrollListener()
    private val dividerDecoration: SimpleVerticalListDivider = SimpleVerticalListDivider()

    init {
        setHasFixedSize(true)
        layoutManager = ScrollPauseLinearLayoutManager(context)
        setLayoutManager(layoutManager)
        adapter = ChannelListItemAdapter(parseStyleAttributes(context, attrs))
        setSwipeListener(ChannelItemSwipeListener(this, layoutManager))
        setMoreOptionsClickListener { channel ->
            context.getFragmentManager()?.let { fragmentManager ->
                ChannelActionsDialogFragment
                    .newInstance(channel.cid, !channel.isDirectMessaging())
                    .show(fragmentManager, null)
            }
        }
        addItemDecoration(dividerDecoration)
    }

    private fun parseStyleAttributes(context: Context, attrs: AttributeSet?): ChannelListViewStyle {
        // parse the attributes
        return ChannelListViewStyle(context, attrs).apply {
            // use the background color as a default for the avatar border
            if (avatarBorderColor == -1) {
                background.let { channelViewBackground ->
                    avatarBorderColor = when (channelViewBackground) {
                        is ColorDrawable -> channelViewBackground.color
                        else -> Color.WHITE
                    }
                }
            }
        }
    }

    private fun requireAdapter(): ChannelListItemAdapter {
        val logger = ChatLogger.get("ChannelListView::requireAdapter")
        val channelAdapter = adapter

        require(channelAdapter != null) {
            logger.logE("Required adapter was null")
        }

        require(channelAdapter is ChannelListItemAdapter) {
            logger.logE("Adapter must be an instance of ChannelListItemAdapter")
        }

        return channelAdapter.cast()
    }

    public fun setViewHolderFactory(factory: ChannelListItemViewHolderFactory) {
        requireAdapter().viewHolderFactory = factory
    }

    public fun setChannelClickListener(listener: ChannelClickListener?) {
        requireAdapter().listenerProvider.channelClickListener = listener ?: ChannelClickListener.DEFAULT
    }

    public fun setChannelLongClickListener(listener: ChannelClickListener?) {
        requireAdapter().listenerProvider.channelLongClickListener = listener ?: ChannelClickListener.DEFAULT
    }

    public fun setUserClickListener(listener: UserClickListener?) {
        requireAdapter().listenerProvider.userClickListener = listener ?: UserClickListener.DEFAULT
    }

    public fun setChannelDeleteClickListener(listener: ChannelClickListener?) {
        requireAdapter().listenerProvider.deleteClickListener = listener ?: ChannelClickListener.DEFAULT
    }

    public fun setMoreOptionsClickListener(listener: ChannelClickListener?) {
        requireAdapter().listenerProvider.moreOptionsClickListener = listener ?: ChannelClickListener.DEFAULT
    }

    public fun setSwipeListener(listener: SwipeListener?) {
        requireAdapter().listenerProvider.swipeListener = listener ?: SwipeListener.DEFAULT
    }

    public fun setItemSeparator(@DrawableRes drawableResource: Int) {
        dividerDecoration.drawableResource = drawableResource
    }

    public fun setItemSeparatorHeight(height: Int) {
        dividerDecoration.drawableHeight = height
    }

    public fun setOnEndReachedListener(listener: EndReachedListener?) {
        endReachedListener = listener
        observeListEndRegion()
    }

    private fun observeListEndRegion() {
        addOnScrollListener(scrollListener)
    }

    public fun setPaginationEnabled(enabled: Boolean) {
        scrollListener.setPaginationEnabled(enabled)
    }

    public fun reachedEndOfChannels(endReached: Boolean) {
        requireAdapter().endReached = endReached
    }

    public fun setChannels(channels: List<Channel>) {
        requireAdapter().submitList(channels)
    }

    public override fun onVisibilityChanged(view: View, visibility: Int) {
        super.onVisibilityChanged(view, visibility)
        if (visibility == 0 && adapter != null) requireAdapter().notifyDataSetChanged()
    }

    public fun interface UserClickListener {
        public companion object {
            public val DEFAULT: UserClickListener = UserClickListener {}
        }

        public fun onUserClick(user: User)
    }

    public fun interface ChannelClickListener {
        public companion object {
            public val DEFAULT: ChannelClickListener = ChannelClickListener {}
        }

        public fun onClick(channel: Channel)
    }

    public fun interface EndReachedListener {
        public fun onEndReached()
    }

    private inner class EndReachedScrollListener : OnScrollListener() {
        private var enabled = false
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (SCROLL_STATE_IDLE == newState) {
                val linearLayoutManager = getLayoutManager()?.cast<LinearLayoutManager>()
                val lastVisiblePosition = linearLayoutManager?.findLastVisibleItemPosition()
                val reachedTheEnd = requireAdapter().itemCount - 1 == lastVisiblePosition
                if (reachedTheEnd && enabled) {
                    endReachedListener?.onEndReached()
                }
            }
        }

        fun setPaginationEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }

    public interface SwipeListener {
        /**
         * Invoked when a swipe is detected.
         *
         * @param viewHolder the view holder that is being swiped
         * @param adapterPosition the internal adapter position of the item being bound
         * @param x the raw X of the swipe origin; null may indicate the call isn't from user interaction
         * @param y the raw Y of the swipe origin; null may indicate the call isn't from user interaction
         */
        public fun onSwipeStarted(viewHolder: SwipeViewHolder, adapterPosition: Int, x: Float? = null, y: Float? = null)

        /**
         * Invoked after a swipe has been detected, and movement is occurring.
         *
         * @param viewHolder the view holder that is being swiped
         * @param adapterPosition the internal adapter position of the item being bound
         * @param dX the change from the previous swipe touch event to the current
         * @param totalDeltaX the change from the first touch event to the current
         */
        public fun onSwipeChanged(viewHolder: SwipeViewHolder, adapterPosition: Int, dX: Float, totalDeltaX: Float)

        /**
         * Invoked when a swipe is successfully completed naturally, without cancellation.
         *
         * @param viewHolder the view holder that is being swiped
         * @param adapterPosition the internal adapter position of the item being bound
         * @param x the raw X of the swipe origin; null may indicate the call isn't from user interaction
         * @param y the raw Y of the swipe origin; null may indicate the call isn't from user interaction
         */
        public fun onSwipeCompleted(
            viewHolder: SwipeViewHolder,
            adapterPosition: Int,
            x: Float? = null,
            y: Float? = null
        )

        /**
         * Invoked when a swipe is canceled.
         *
         * @param viewHolder the view holder that is being swiped
         * @param adapterPosition the internal adapter position of the item being bound
         * @param x the raw X of the swipe origin; null may indicate the call isn't from user interaction
         * @param y the raw Y of the swipe origin; null may indicate the call isn't from user interaction         */
        public fun onSwipeCanceled(
            viewHolder: SwipeViewHolder,
            adapterPosition: Int,
            x: Float? = null,
            y: Float? = null
        )

        /**
         * Invoked in order to set the [viewHolder]'s initial state when bound. This supports view holder reuse.
         * When items are scrolled off-screen and the view holder is reused, it becomes important to
         * track the swiped state and determine if the view holder should appear as swiped for the item
         * being bound.
         *
         * @param viewHolder the view holder being bound
         * @param adapterPosition the internal adapter position of the item being bound
         */
        public fun onRestoreSwipePosition(viewHolder: SwipeViewHolder, adapterPosition: Int)

        public companion object {

            public val DEFAULT: SwipeListener = object : SwipeListener {
                override fun onSwipeStarted(
                    viewHolder: SwipeViewHolder,
                    adapterPosition: Int,
                    x: Float?,
                    y: Float?
                ) {
                    // no-op
                }

                override fun onSwipeChanged(
                    viewHolder: SwipeViewHolder,
                    adapterPosition: Int,
                    dX: Float,
                    totalDeltaX: Float
                ) {
                    // no-op
                }

                override fun onSwipeCompleted(
                    viewHolder: SwipeViewHolder,
                    adapterPosition: Int,
                    x: Float?,
                    y: Float?
                ) {
                    // no-op
                }

                override fun onSwipeCanceled(
                    viewHolder: SwipeViewHolder,
                    adapterPosition: Int,
                    x: Float?,
                    y: Float?
                ) {
                    // no-op
                }

                override fun onRestoreSwipePosition(viewHolder: SwipeViewHolder, adapterPosition: Int) {
                    // no-op
                }
            }
        }
    }
}
