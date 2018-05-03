package org.tokend.template.util.error_handlers

interface ErrorHandler {
    fun handle(error: Throwable): Boolean
    fun getErrorMessage(error: Throwable): String?
}