<?php

namespace App;

use App\Models\Bar;

class Foo
{
    /** @var array<Bar> */
    private array $bars = [];

    /** @var array<int, Bar> */
    private array $barsById = [];

    /** @var Bar[] */
    public $legacyBars;

    /** @var list<string> */
    protected array $names = [];

    /**
     * @var Bar $first
     * @var Bar|null $second
     */
    public $first, $second;
}
