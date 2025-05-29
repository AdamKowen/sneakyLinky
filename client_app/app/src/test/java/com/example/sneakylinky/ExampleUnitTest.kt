package com.example.sneakylinky

import com.example.sneakylinky.service.extractDomain
import org.junit.Test

import org.junit.Assert.*


class ExampleUnitTest {

    val testUrl = "https://www.Example.com/path/page"
    @Test
    fun domain_extraction() {
        assertEquals("www.example.com", extractDomain(testUrl))
    }
}