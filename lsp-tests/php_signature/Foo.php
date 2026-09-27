<?php

namespace App;

use App\Models\Bar;

class Foo
{
    public function __construct(private Bar $bar) {}

    public function run(?Bar $bar, int|Bar $mixed, string ...$rest): ?Bar
    {
    }
}

function helper(Bar $bar): Bar
{
}
