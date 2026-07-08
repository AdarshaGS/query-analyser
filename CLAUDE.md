# CLAUDE.md

# SQL Optimizer Project Instructions

This repository implements an enterprise SQL Optimizer that analyzes SQL queries using:

- SQL parsing
- Metadata extraction
- MySQL EXPLAIN JSON
- Rule-based analysis
- AI recommendations
- SQL rewrite suggestions

The objective is to produce accurate, explainable, deterministic SQL optimization recommendations.

---

# Primary Goal

Always optimize for:

1. Correctness
2. Explainability
3. Maintainability
4. Performance

Never sacrifice correctness for cleverness.

---

# Architecture

The request flow is:

SQL

↓

Validation

↓

Preprocessing

↓

Metadata Extraction

↓

EXPLAIN JSON

↓

Rule Engine

↓

AI Analysis

↓

Merge Results

↓

SqlAnalysisResponse

Do not change this pipeline unless explicitly requested.

---

# Technology Stack

Java 17

Spring Boot 3

MySQL 8

Jackson

Maven

JUnit 5

Mockito

SLF4J Logging

---

# Coding Standards

Always:

- Constructor Injection
- Immutable DTOs whenever practical
- Small focused methods
- Single Responsibility Principle
- Clear naming
- Early returns
- Guard clauses
- Java Streams only when readability improves

Never:

- Field Injection
- System.out.println()
- Magic numbers
- Duplicate business logic
- Large methods (>80 lines when avoidable)
- Nested if statements when guard clauses are clearer

---

# Logging

INFO

Business flow

DEBUG

SQL
Generated SQL
EXPLAIN output

WARN

Unexpected but recoverable situations

ERROR

Failures with stack traces

Never log:

- passwords
- tokens
- sensitive credentials

---

# SQL Rules

Recommendations must always be technically correct.

Never recommend an index unless it is justified.

Consider:

- join order
- access type
- cardinality
- filtered %
- possible_keys
- key
- rows examined
- temporary tables
- filesort
- covering indexes
- composite indexes
- index selectivity

Do not recommend FORCE INDEX unless there is strong evidence.

Avoid recommending indexes for tiny lookup tables.

---

# Rule Engine

The Rule Engine is deterministic.

AI must NOT contradict deterministic findings.

If AI disagrees with the Rule Engine:

- keep deterministic issues
- AI may add explanations
- AI may improve wording
- AI may suggest additional improvements

Never delete Rule Engine findings.

---

# AI Prompting

When modifying prompts:

Prefer:

Specific instructions

Expected JSON structure

Examples

Strict formatting

Avoid:

Long paragraphs

Repeated instructions

Ambiguous wording

---

# JSON Responses

Preserve response compatibility.

Do not rename JSON fields unless requested.

Do not remove fields.

Avoid changing DTO contracts.

---

# Refactoring

Only refactor when requested.

When refactoring:

Maintain behaviour.

Preserve APIs.

Avoid unrelated formatting changes.

Do not move classes unnecessarily.

---

# Testing

When fixing bugs:

1. Reproduce
2. Fix
3. Verify

Prefer:

Unit Tests

Integration Tests

Edge Cases

Do not skip verification.

---

# Before Writing Code

Always determine:

- What is the user's actual goal?
- Which classes are affected?
- Can the change be localized?
- Is there an existing implementation?
- Is a simpler solution possible?

If requirements are ambiguous, ask.

Do not guess.

---

# Code Review Checklist

Before finishing verify:

✓ Compiles

✓ No duplicate logic

✓ No unnecessary abstractions

✓ No API breakage

✓ Logging appropriate

✓ Existing style preserved

✓ Imports cleaned

✓ Tests updated if needed

✓ DTO compatibility maintained

✓ Performance not degraded

---

# Repository Conventions

Preferred package organization:

analysis/

rules/

rewrite/

prompt/

metadata/

explain/

service/

dto/

Keep related logic together.

Avoid utility classes unless they are genuinely reusable.

---

# Communication Style

When answering:

- Explain reasoning briefly.
- Mention assumptions.
- Identify trade-offs.
- Suggest simpler alternatives if appropriate.
- If something is unclear, ask before coding.

Do not make speculative changes.

Do not optimize code that was not requested.

Focus only on the requested task.