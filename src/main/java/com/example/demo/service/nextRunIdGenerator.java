package com.example.demo.service;

import java.math.BigDecimal;
import java.util.Random;

import org.springframework.stereotype.Service;

@Service
public class nextRunIdGenerator {
	private final Random random = new Random();

	public BigDecimal generateNextRunId() {

		int randomNumber = random.nextInt(9999 - 1000 + 1) + 1000;

		return new BigDecimal(randomNumber);
	}
}
