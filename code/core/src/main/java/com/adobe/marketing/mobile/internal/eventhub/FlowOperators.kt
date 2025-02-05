package com.adobe.marketing.mobile.internal.eventhub

import com.adobe.marketing.mobile.Event
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
fun Flow<Event>.pauseIf(predicate: suspend (Event) -> Boolean): Flow<Event> {

    return flow {
        coroutineScope {
            val events = ArrayDeque<Event>()
            try {
                val upstreamValues = produce {
                    collect {
                        send(it)
                    }
                }

                while (isActive) {

                    select<Unit> {
                        upstreamValues.onReceive {
                            events.add(it)
                            while (events.isNotEmpty()) {
                                val oldestEvent = events.first()
                                if (predicate(oldestEvent)) {
                                    emit(events.removeFirst())
                                } else {
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                // drain remaining events
//                if (events.isNotEmpty()) emit(events.toList())
            } finally {
//                tickerChannel.cancel()
            }
        }
    }
}