<?php

namespace App;

use App\Models\Bar;

class Foo
{
    /**
     * @return Bar
     */
    public function hinted(): ?Foo
    {
        return new Bar;
    }

    /**
     * @return Bar|null
     */
    public function documented()
    {
        return new Foo;
    }

    public function returned()
    {
        if (true) {
            return 1;
        }

        return new Bar;
    }

    public function fluent(): static
    {
        return $this;
    }
}

/**
 * @return list<Bar>
 */
function bars()
{
}

function made()
{
    $bar = new Bar;

    return $bar;
}
