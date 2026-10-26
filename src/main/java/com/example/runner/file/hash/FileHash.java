package com.example.runner.file.hash;

import com.example.runner.PojaGenerated;

@PojaGenerated
public record FileHash(FileHashAlgorithm algorithm, String value) {}
