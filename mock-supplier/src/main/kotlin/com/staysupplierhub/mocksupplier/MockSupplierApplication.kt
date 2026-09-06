package com.staysupplierhub.mocksupplier

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MockSupplierApplication

fun main(args: Array<String>) {
    runApplication<MockSupplierApplication>(*args)
}
