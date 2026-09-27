<?php

namespace App;

/**
 * Makes a foo.
 *
 * @param int $bar
 * @return Foo
 */
function makeFoo($bar)
{
}

class Foo
{
    /**
     * Runs the bar.
     */
    #[Attr]
    public static function run() {}
}
