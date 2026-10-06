<?php

namespace App;

use App\Models\Bar;

class Foo
{
    /** @var array<Bar> */
    private array $bars = [];

    /**
     * @var array<int, Bar>
     */
    private array $byId = [];
}
