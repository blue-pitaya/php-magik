<?php

namespace App;

use App\Models\Bar;

class Foo
{
    public static function baz($a, $b) {}

    public function run($a)
    {
        return self::baz($a, Bar::QUX);
    }
}
