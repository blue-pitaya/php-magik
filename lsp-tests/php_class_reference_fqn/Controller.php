<?php

namespace App\Http;

use App\Data\Users\ShowProps;

class Controller
{
    public function show(): ShowProps
    {
        return new ShowProps;
    }

    public function title(ShowProps $props)
    {
        return $props->title();
    }
}
