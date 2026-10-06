<?php

class Modifiers
{
    public static int $count = 0;

    protected readonly string $label;

    var $legacy;

    private int $x, $y;

    public function __construct(private readonly Engine $engine) {}
}
