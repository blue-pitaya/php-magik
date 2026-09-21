<?php

class Engine
{
    public int $power = 120;
}

class Container
{
    public function engine(): Engine
    {
        return new Engine();
    }
}
