<?php

namespace App;

use App\Models\Bar;

class Baz
{
    public function run()
    {
        $this->result = Bar::get();
    }
}
