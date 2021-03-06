/*
 * Trident - A Multithreaded Server Alternative
 * Copyright 2014 The TridentSDK Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.tridentsdk.event;

import com.esotericsoftware.reflectasm.MethodAccess;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import net.tridentsdk.Handler;
import net.tridentsdk.Trident;
import net.tridentsdk.concurrent.ConcurrentCache;
import net.tridentsdk.docs.InternalUseOnly;
import net.tridentsdk.plugin.TridentPlugin;
import net.tridentsdk.plugin.annotation.IgnoreRegistration;
import net.tridentsdk.util.TridentLogger;

import javax.annotation.concurrent.ThreadSafe;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * The server's event handler, should only be created once, and only once by the server only
 *
 * @author The TridentSDK Team
 */
@ThreadSafe
public class EventHandler {
    private static final Comparator<EventReflector> COMPARATOR = new EventReflector(null, null, 0, null, null, null);
    public static final Callable<Queue<EventReflector>> CREATE_QUEUE = new Callable<Queue<EventReflector>>() {
        @Override
        public Queue<EventReflector> call() throws Exception {
            return new PriorityBlockingQueue<>(128, COMPARATOR);
        }
    };

    private final ConcurrentCache<Class<? extends Event>, Queue<EventReflector>> callers = ConcurrentCache.create();
    private final ConcurrentCache<Class<?>, MethodAccess> accessors = ConcurrentCache.create();

    private EventHandler() {
        if (!Trident.isTrident()) {
            TridentLogger.error(new IllegalAccessException("EventManager must be initiated by TridentSDK!"));
        }
    }

    /**
     * Creates a new event handler, should only be used internally
     *
     * @return the new event handler
     */
    @InternalUseOnly
    public static EventHandler create() {
        return new EventHandler();
    }

    /**
     * Normally not needed to be used. Plugin listeners are automatically registered when they are loaded.
     *
     * @param listener the listener instance to use to register
     */
    @InternalUseOnly
    public void registerListener(TridentPlugin plugin, Listener listener) {
        final Class<?> c = listener.getClass();
        HashMultimap<Class<? extends Event>, EventReflector> reflectors = reflectorsFrom(plugin, listener, c);

        for (Class<? extends Event> eventClass : reflectors.keys()) {
            Queue<EventReflector> eventCallers = callers.retrieve(eventClass, CREATE_QUEUE);
            eventCallers.addAll(reflectors.get(eventClass));
        }
    }

    private HashMultimap<Class<? extends Event>, EventReflector> reflectorsFrom(TridentPlugin plugin, Listener listener,
            final Class<?> c) {
        MethodAccess access = accessors.retrieve(c, new Callable<MethodAccess>() {
            @Override
            public MethodAccess call() throws Exception {
                return MethodAccess.get(c);
            }
        });

        Method[] methods = c.getDeclaredMethods();

        HashMultimap<Class<? extends Event>, EventReflector> map = HashMultimap.create(11, 11);
        for (int i = 0, n = methods.length; i < n; i++) {
            Method method = methods[i];
            if (method.isAnnotationPresent(IgnoreRegistration.class)) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1)
                continue;

            Class<?> type = parameterTypes[0];

            if (!Event.class.isAssignableFrom(type))
                continue;

            Class<? extends Event> eventClass = type.asSubclass(Event.class);
            ListenerData handler = method.getAnnotation(ListenerData.class);
            Importance importance = handler == null ? Importance.MEDIUM : handler.importance();

            EventReflector registeredListener = new EventReflector(access, plugin, i, listener, eventClass, importance);
            map.put(eventClass, registeredListener);
        }

        return map;
    }

    /**
     * Calls an event
     *
     * @param event the event to call
     */
    public void fire(final Event event) {
        final Queue<EventReflector> listeners = callers.retrieve(event.getClass());
        if (listeners == null) return;

        final CountDownLatch latch = new CountDownLatch(1);

        Handler.forPlugins().executor().addTask(new Runnable() {
            @Override
            public void run() {
                for (EventReflector listener : listeners) {
                    listener.reflect(event);
                }

                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes the listener from the caller queue, preventing it from being invoked
     *
     * @param cls the listener class to unregister
     */
    public void unregister(Class<? extends Listener> cls) {
        for (Map.Entry<Class<? extends Event>, Queue<EventReflector>> entry : this.callers.entries()) {
            for (Iterator<EventReflector> iterator = entry.getValue().iterator(); iterator.hasNext(); ) {
                EventReflector it = iterator.next();
                if (it.instance().getClass().equals(cls)) {
                    iterator.remove();
                    break;
                }
            }
        }
    }

    /**
     * Acquires a list of the listeners registered to the plugin provided
     *
     * @param plugin the plugin to find the listeners for
     * @return the listeners for that plugin
     */
    public Map<Class<? extends Listener>, Listener> listenersFor(TridentPlugin plugin) {
        Map<Class<? extends Listener>, Listener> listeners = Maps.newHashMap();
        for (Queue<EventReflector> reflectors : callers.values()) {
            for (EventReflector reflector : reflectors) {
                if (reflector.plugin().equals(plugin)) {
                    listeners.put(reflector.instance().getClass(), reflector.instance());
                }
            }
        }

        return listeners;
    }
}