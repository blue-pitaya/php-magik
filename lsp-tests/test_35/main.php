<?php

function format()
{
    return 'global';
}

class Formatter
{
    public function format()
    {
        return 'method';
    }
}

function run(Formatter $f)
{
    format();
    $f->format();
}
