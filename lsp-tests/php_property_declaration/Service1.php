<?php

namespace App;

class Service1
{
    public int $a;

    public int $b;

    private string $c = 'x';

    public function __construct(private Engine $engine) {}
}
