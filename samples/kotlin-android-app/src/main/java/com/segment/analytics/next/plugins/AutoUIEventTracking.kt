package com.segment.analytics.next.plugins

import android.app.Activity
import android.view.*
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.platform.Plugin
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class AutoUIEventTracking(
    private val uiEventHandler: UIEventHandler,
    val swizzleEvents: Set<UIEvent> = emptySet()
    ): Plugin {
    override val type: Plugin.Type = Plugin.Type.Utility

    override lateinit var analytics: Analytics

    /**
     * The variable name of listenerInfo used internally in [android.view.View]
     *
     * **Note**: this field is intentionally set to modifiable, so that
     *          in the case where the variable name is changed in Android
     *          SDK, users can update the name accordingly by themselves.
     *          see [android.view.View] for the actual name.
     */
    var listenerInfoName = "mListenerInfo"

    fun autoTracking(activity: Activity) {
        if (swizzleEvents.isEmpty()) return

        val root = activity.findViewById<View>(android.R.id.content).rootView as ViewGroup
        swizzle(root)
    }

    private fun swizzle(view: View) {
        modify(view)

        if (view !is ViewGroup) return

        for (i in 0 until view.childCount) {
            swizzle(view.getChildAt(i))
        }
    }

    private fun modify(view: View) {
        val listenerInfoField = View::class.java.getDeclaredField(listenerInfoName)
        listenerInfoField.isAccessible = true
        val listenerInfo = listenerInfoField.get(view) ?: return

        for (uiEvent in swizzleEvents) {
            delegate(listenerInfo, uiEvent.value)
        }

        listenerInfoField.isAccessible = false
    }

    private fun delegate(listenerInfo: Any, field: String) {
        val listenerFiled = listenerInfo.javaClass.getDeclaredField(field)
        listenerFiled.isAccessible = true

        listenerFiled.get(listenerInfo)?.run {
            val interceptor = EventInterceptor(this, analytics, uiEventHandler)
            val proxy = Proxy.newProxyInstance(javaClass.classLoader, javaClass.interfaces, interceptor)
            listenerFiled.set(listenerInfo, proxy)
        }

        listenerFiled.isAccessible = false
    }

    class EventInterceptor(
        private val realObject: Any,
        private val analytics: Analytics,
        private val handler: UIEventHandler) : InvocationHandler {

        override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any {
            if (args == null || args.isEmpty()) {
                method?.invoke(realObject, args)
                return realObject
            }

            when (realObject) {
                is View.OnKeyListener -> {
                    handler.onKey(analytics, args[0] as View, args[1] as Int, args[2] as KeyEvent)
                }
                is View.OnTouchListener -> {
                    handler.onTouch(analytics, args[0] as View, args[1] as MotionEvent)
                }
                is View.OnHoverListener -> {
                    handler.onHover(analytics, args[0] as View, args[1] as MotionEvent)
                }
                is View.OnGenericMotionListener -> {
                    handler.onGenericMotion(analytics, args[0] as View, args[1] as MotionEvent)
                }
                is View.OnLongClickListener -> {
                    handler.onLongClick(analytics, args[0] as View)
                }
                is View.OnDragListener -> {
                    handler.onDrag(analytics, args[0] as View, args[1] as DragEvent)
                }
                is View.OnFocusChangeListener -> {
                    handler.onFocusChange(analytics, args[0] as Boolean)
                }
                is View.OnClickListener -> {
                    handler.onClick(analytics, args[0] as View)
                }
                is View.OnCreateContextMenuListener -> {
                    handler.onCreateContextMenu(analytics, args[0] as ContextMenu, args[1] as View, args[2] as ContextMenu.ContextMenuInfo)
                }
            }

            if (args.size == 1) {
                method?.invoke(realObject, args[0])
            }
            else {
                method?.invoke(realObject, args)
            }

            return realObject
        }
    }

    interface UIEventHandler {
        /**
         * OnKeyListener
         */
        fun onKey(analytics: Analytics, view: View, keyCode: Int, event: KeyEvent) {}

        /**
         * OnTouchListener
         */
        fun onTouch(analytics: Analytics, view: View, event: MotionEvent) {}

        /**
         * OnHoverListener
         */
        fun onHover(analytics: Analytics, view: View, event: MotionEvent) {}

        /**
         * OnGenericMotionListener
         */
        fun onGenericMotion(analytics: Analytics, view: View, event: MotionEvent) {}

        /**
         * OnLongClickListener
         */
        fun onLongClick(analytics: Analytics, view: View) {}

        /**
         * OnDragListener
         */
        fun onDrag(analytics: Analytics, view: View, event: DragEvent)

        /**
         * OnFocusChangeListener
         */
        fun onFocusChange(analytics: Analytics, hasFocus: Boolean)

        /**
         * OnClickListener
         */
        fun onClick(analytics: Analytics, view: View)

        /**
         * OnCreateContextMenuListener
         */
        fun onCreateContextMenu(analytics: Analytics, menu: ContextMenu, view: View, menuInfo: ContextMenu.ContextMenuInfo)
    }

    /**
     * The event listeners that we can swizzle for
     *
     * **Note**: `value` is intentionally set to modifiable, so that
     *          in the case where the listener name is changed in Android
     *          SDK, users can update the name accordingly by themselves.
     *          see [android.view.View.ListenerInfo] for the listener names.
     */
    enum class UIEvent(var value: String) {
        Key("mOnKeyListener"),
        Touch("mOnTouchListener"),
        Hover("mOnHoverListener"),
        GenericMotion("mOnGenericMotionListener"),
        LongClick("mOnLongClickListener"),
        Drag("mOnDragListener"),
        FocusChange("mOnFocusChangeListener"),
        Click("mOnClickListener"),
        CreateContextMenu("mOnCreateContextMenuListener")
    }
}