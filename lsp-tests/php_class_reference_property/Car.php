<?php

namespace App;

use App\Models\Engine;
use App\Models\Engine as Motor;

class Car
{
    public Engine $engine;

    private ?Engine $spare = null;

    public int|Engine $mixed;

    public function __construct(private Engine $promoted, Engine $plain) {}
}
